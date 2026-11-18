package com.otusfoss.mail.feature.notifications.data

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.core.jmap.login.JmapLogin
import com.otusfoss.mail.testing.stalwartLike
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlin.test.assertEquals

internal val FAKE_ROUTE = "r".repeat(43)
private val FAKE_MESSAGE = "m".repeat(43)
private val NEW_ROUTE = """{"id":"$FAKE_ROUTE","secret":"secret"}"""

/**
 * A push bridge that hands out [FAKE_ROUTE] and holds one message until it is acknowledged; each
 * request is added to [paths] as "METHOD path". Unless [verifies] is false, the message is a
 * verification, so the route must turn active once it is acknowledged.
 */
internal fun fakeBridge(
    paths: MutableList<String>,
    verifies: Boolean = true,
): HttpClient {
    var acknowledged = false
    return HttpClient(
        MockEngine { request ->
            val path = request.url.encodedPath
            paths += "${request.method.value} $path"
            if (path.endsWith("/messages/$FAKE_MESSAGE") && request.method == HttpMethod.Delete) acknowledged = true
            bridgeReply(request, path, acknowledged, verifies)
        },
    ) { followRedirects = false }
}

private fun MockRequestHandleScope.bridgeReply(
    request: HttpRequestData,
    path: String,
    acknowledged: Boolean,
    verifies: Boolean,
): HttpResponseData =
    when {
        request.method == HttpMethod.Post -> respond(NEW_ROUTE, HttpStatusCode.Created)
        path == "/v1/routes/$FAKE_ROUTE/messages" -> respond(if (acknowledged) "[]" else """["$FAKE_MESSAGE"]""")
        path == "/v1/messages/$FAKE_MESSAGE" -> respond("ciphertext")
        request.method == HttpMethod.Patch -> activated(request, acknowledged && verifies)
        else -> respond("", HttpStatusCode.NoContent)
    }

/** A route turns active only once a verification was acknowledged. */
private fun MockRequestHandleScope.activated(
    request: HttpRequestData,
    verified: Boolean,
): HttpResponseData {
    assertEquals("{\"active\":$verified}", (request.body as TextContent).text)
    return respond("", HttpStatusCode.NoContent)
}

/**
 * A push bridge that refuses the first registration with 429 and hands out [SECOND_ROUTE] after
 * that, with no pending messages; each request is added to [paths] as "METHOD path".
 */
internal fun busyFirstBridge(paths: MutableList<String>): HttpClient {
    var registrations = 0
    return HttpClient(
        MockEngine { request ->
            paths += "${request.method.value} ${request.url.encodedPath}"
            when {
                request.method != HttpMethod.Post -> respond(if (request.method == HttpMethod.Get) "[]" else "")
                registrations++ == 0 -> respond("", HttpStatusCode.TooManyRequests)
                else -> respond("""{"id":"$SECOND_ROUTE","secret":"secret"}""", HttpStatusCode.Created)
            }
        },
    ) { followRedirects = false }
}

internal val SECOND_ROUTE = "s".repeat(43)

/** A JMAP server that creates, verifies, and destroys one push subscription. */
internal fun fakePushServer(): JmapClient =
    JmapClient(
        stalwartLike(api = { body ->
            when {
                "PushSubscription/get" in body -> pushReply("PushSubscription/get", """{"list":[]}""")
                "\"create\"" in body -> pushReply("PushSubscription/set", CREATED)
                "\"destroy\"" in body -> pushReply("PushSubscription/set", """{"destroyed":["ps1"]}""")
                else -> pushReply("PushSubscription/set", """{"updated":{"ps1":null}}""")
            }
        }),
    )

private fun pushReply(
    method: String,
    arguments: String,
): String = """{"methodResponses":[["$method",$arguments,"c0"]]}"""

/** Ada's login on the [fakePushServer]. */
internal fun adaLogins(): InMemoryLoginStore =
    InMemoryLoginStore().apply {
        save(JmapLogin("ada", "https://mail.example.org/jmap/session", "ada@example.org", "Ada"), "secret")
    }

/** Ada's and Bob's logins on the [fakePushServer]. */
internal fun adaAndBobLogins(): InMemoryLoginStore =
    adaLogins().apply {
        save(JmapLogin("bob", "https://mail.example.org/jmap/session", "bob@example.org", "Bob"), "secret")
    }

private const val CREATED = """{"created":{"push":{"id":"ps1","expires":"2030-01-01T00:00:00Z"}}}"""
