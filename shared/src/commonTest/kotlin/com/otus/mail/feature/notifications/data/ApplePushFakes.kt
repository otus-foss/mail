package com.otus.mail.feature.notifications.data

import com.otus.jmap.JmapClient
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.testing.stalwartLike
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
 * A push bridge that hands out [FAKE_ROUTE] and holds one verification message until it is
 * acknowledged; each request is added to [paths] as "METHOD path".
 */
internal fun fakeBridge(paths: MutableList<String>): HttpClient {
    var acknowledged = false
    return HttpClient(
        MockEngine { request ->
            val path = request.url.encodedPath
            paths += "${request.method.value} $path"
            if (path.endsWith("/messages/$FAKE_MESSAGE") && request.method == HttpMethod.Delete) acknowledged = true
            bridgeReply(request, path, acknowledged)
        },
    ) { followRedirects = false }
}

private fun MockRequestHandleScope.bridgeReply(
    request: HttpRequestData,
    path: String,
    acknowledged: Boolean,
): HttpResponseData =
    when {
        request.method == HttpMethod.Post -> respond(NEW_ROUTE, HttpStatusCode.Created)
        path == "/v1/routes/$FAKE_ROUTE/messages" -> respond(if (acknowledged) "[]" else """["$FAKE_MESSAGE"]""")
        path == "/v1/messages/$FAKE_MESSAGE" -> respond("ciphertext")
        request.method == HttpMethod.Patch -> activated(request, acknowledged)
        else -> respond("", HttpStatusCode.NoContent)
    }

/** A route turns active only once its verification was acknowledged. */
private fun MockRequestHandleScope.activated(
    request: HttpRequestData,
    acknowledged: Boolean,
): HttpResponseData {
    assertEquals("{\"active\":$acknowledged}", (request.body as TextContent).text)
    return respond("", HttpStatusCode.NoContent)
}

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

private const val CREATED = """{"created":{"push":{"id":"ps1","expires":"2030-01-01T00:00:00Z"}}}"""
