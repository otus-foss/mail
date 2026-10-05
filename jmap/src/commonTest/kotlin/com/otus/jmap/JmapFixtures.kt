package com.otus.jmap

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.serialization.Serializable

val ADA = JmapAuth.Basic("ada@example.org", "secret")

/** Shaped like Stalwart's session: relative apiUrl, primary accounts per capability. */
fun session(
    apiUrl: String = "/jmap/",
    state: String = "s1",
    mail: Boolean = true,
): String =
    """
    {"capabilities": {"urn:ietf:params:jmap:core": {}${if (mail) ", \"urn:ietf:params:jmap:mail\": {}" else ""}},
     "accounts": {"d1": {"name": "Ada", "isPersonal": true,
       "accountCapabilities": {${if (mail) "\"urn:ietf:params:jmap:mail\": {}" else ""}}}},
     "primaryAccounts": {${if (mail) "\"urn:ietf:params:jmap:mail\": \"d1\"" else ""}},
     "username": "ada@example.org", "apiUrl": "$apiUrl", "state": "$state"}
    """.trimIndent()

class Recorded(
    val host: String,
    val path: String,
    val authorized: Boolean,
    val body: String,
)

/** A JMAP server like Stalwart: `/.well-known/jmap` answers with a relative 307 to the session. */
fun server(api: (String) -> String = { error("unexpected API call") }): Pair<MockEngine, MutableList<Recorded>> {
    val requests = mutableListOf<Recorded>()
    val engine =
        MockEngine { request ->
            val record = request.record()
            requests += record
            when {
                request.headers[HttpHeaders.Authorization] != ADA.header() -> respond("", HttpStatusCode.Unauthorized)
                record.path == "/.well-known/jmap" -> redirect("/jmap/session")
                record.path == "/jmap/session" -> json(session())
                record.path == "/jmap/" -> json(api(record.body))
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
    return engine to requests
}

fun HttpRequestData.record() =
    Recorded(
        url.host,
        url.encodedPath,
        headers[HttpHeaders.Authorization] != null,
        (body as? TextContent)?.text.orEmpty(),
    )

fun MockRequestHandleScope.json(text: String): HttpResponseData =
    respond(text, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

fun MockRequestHandleScope.redirect(location: String): HttpResponseData =
    respond("", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, location))

@Serializable
data class Mailbox(
    val id: String,
    val role: String? = null,
)

val MAILBOX = JmapType("Mailbox", JmapCapability.MAIL, Mailbox.serializer())
