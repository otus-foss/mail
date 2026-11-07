package com.otus.mail.testing

import com.otus.jmap.login.JmapLogin
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher

/** Ada's login at the mock server, which accepts her with the password `secret`. */
val ADA_LOGIN = JmapLogin("ada", "https://mail.example.org/.well-known/jmap", "ada@example.org", "Ada")

/** Basic auth for ada@example.org with the password "secret". */
const val ADA_SECRET_HEADER = "Basic YWRhQGV4YW1wbGUub3JnOnNlY3JldA=="

const val STALWART_SESSION = """
{"capabilities": {"urn:ietf:params:jmap:core": {}, "urn:ietf:params:jmap:mail": {}},
 "accounts": {"d333333": {"name": "ada@example.org", "isPersonal": true,
   "accountCapabilities": {"urn:ietf:params:jmap:mail": {}}}},
 "primaryAccounts": {"urn:ietf:params:jmap:mail": "d333333"},
 "username": "ada@example.org", "apiUrl": "/jmap/", "state": "s1"}
"""

/** [STALWART_SESSION] with submission on the session and the mail account, so it can send. */
val SUBMISSION_SESSION =
    STALWART_SESSION.replace(
        "\"urn:ietf:params:jmap:mail\": {}}",
        "\"urn:ietf:params:jmap:mail\": {}, \"urn:ietf:params:jmap:submission\": {}}",
    )

/** [STALWART_SESSION] with a blob download endpoint, which [stalwartLike] serves from its files. */
val DOWNLOAD_SESSION =
    STALWART_SESSION.replace(
        "\"state\": \"s1\"}",
        "\"state\": \"s1\", \"downloadUrl\": \"/download/{accountId}/{blobId}/{name}?type={type}\"}",
    )

/** A session whose only account holds contacts, so the login has no mail account. */
const val CONTACTS_ONLY_SESSION = """
{"capabilities": {"urn:ietf:params:jmap:core": {}, "urn:ietf:params:jmap:contacts": {}},
 "accounts": {"c1": {"name": "ada@example.org", "isPersonal": true,
   "accountCapabilities": {"urn:ietf:params:jmap:contacts": {}}}},
 "primaryAccounts": {"urn:ietf:params:jmap:contacts": "c1"},
 "username": "ada@example.org", "apiUrl": "/jmap/", "state": "s1"}
"""

/**
 * A mock server shaped like Stalwart: `/.well-known/jmap` answers with a relative 307, and the
 * session uses a relative apiUrl. [api] receives each API request body.
 */
fun stalwartLike(
    session: String = STALWART_SESSION,
    api: (String) -> String = { error("unexpected API call: $it") },
): MockEngine = stalwartLike(session, emptyMap(), api)

/**
 * [stalwartLike] that also serves [files], by blob id, at [DOWNLOAD_SESSION]'s download URL. It
 * answers on [dispatcher], or on Ktor's IO threads without one.
 */
fun stalwartLike(
    session: String,
    files: Map<String, ByteArray>,
    api: (String) -> String,
    dispatcher: CoroutineDispatcher? = null,
): MockEngine =
    MockEngine(
        MockEngineConfig().apply {
            this.dispatcher = dispatcher
            addHandler { request ->
                val json = headersOf(HttpHeaders.ContentType, "application/json")
                when {
                    request.headers[HttpHeaders.Authorization] != ADA_SECRET_HEADER -> {
                        respond("", HttpStatusCode.Unauthorized)
                    }

                    request.url.encodedPath == "/.well-known/jmap" -> {
                        respond("", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, "/jmap/session"))
                    }

                    request.url.encodedPath == "/jmap/session" -> {
                        respond(session, HttpStatusCode.OK, json)
                    }

                    request.url.encodedPath == "/jmap/" -> {
                        respond(api((request.body as TextContent).text), HttpStatusCode.OK, json)
                    }

                    request.url.encodedPath.startsWith("/download/") -> {
                        val blob = files[request.url.encodedPath.split('/')[3]]
                        if (blob == null) respond("", HttpStatusCode.NotFound) else respond(blob, HttpStatusCode.OK)
                    }

                    else -> {
                        respond("", HttpStatusCode.NotFound)
                    }
                }
            }
        },
    )
