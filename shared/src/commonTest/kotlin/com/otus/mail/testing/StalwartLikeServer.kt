package com.otus.mail.testing

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf

/** Basic auth for ada@example.org with the password "secret". */
const val ADA_SECRET_HEADER = "Basic YWRhQGV4YW1wbGUub3JnOnNlY3JldA=="

const val STALWART_SESSION = """
{"capabilities": {"urn:ietf:params:jmap:core": {}, "urn:ietf:params:jmap:mail": {}},
 "accounts": {"d333333": {"name": "ada@example.org", "isPersonal": true,
   "accountCapabilities": {"urn:ietf:params:jmap:mail": {}}}},
 "primaryAccounts": {"urn:ietf:params:jmap:mail": "d333333"},
 "username": "ada@example.org", "apiUrl": "/jmap/", "state": "s1"}
"""

/**
 * A mock server shaped like Stalwart: `/.well-known/jmap` answers with a relative 307, and the
 * session uses a relative apiUrl. [api] receives each API request body.
 */
fun stalwartLike(
    session: String = STALWART_SESSION,
    api: (String) -> String = { error("unexpected API call: $it") },
): MockEngine =
    MockEngine { request ->
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

            else -> {
                respond("", HttpStatusCode.NotFound)
            }
        }
    }
