package com.otusfoss.mail.feature.notifications.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApplePushBridgeTest {
    private val route = ApplePushRoute("a".repeat(43), "private-management-capability")

    @Test
    fun registeringDoesNotSendMailCredentialsAndManagementUsesBearerToken() =
        runTest {
            val requests = mutableListOf<String>()
            val http =
                HttpClient(
                    MockEngine { request ->
                        requests += request.url.encodedPath
                        if (requests.size == 1) {
                            assertEquals(null, request.headers[HttpHeaders.Authorization])
                            respond("""{"id":"${route.id}","secret":"${route.secret}"}""", HttpStatusCode.Created)
                        } else {
                            assertEquals("Bearer ${route.secret}", request.headers[HttpHeaders.Authorization])
                            respond("", HttpStatusCode.NoContent)
                        }
                    },
                ) { followRedirects = false }
            val bridge = ApplePushBridge("https://push.example", http)
            assertEquals(route, bridge.register("token", "key"))
            bridge.active(route, true)
            bridge.delete(route)
            assertEquals(listOf("/v1/routes", "/v1/routes/${route.id}", "/v1/routes/${route.id}"), requests)
            http.close()
        }

    @Test
    fun expiredRouteCanBeReplacedAndRedirectsAreRejected() =
        runTest {
            val expired = HttpClient(MockEngine { respond("", HttpStatusCode.Gone) })
            assertFailsWith<ApplePushRouteExpired> {
                ApplePushBridge("https://push.example", expired).active(route, false)
            }
            expired.close()
            var calls = 0
            val redirected =
                HttpClient(
                    MockEngine {
                        calls++
                        respond(
                            "",
                            HttpStatusCode.Found,
                            headersOf(HttpHeaders.Location, "https://other.example"),
                        )
                    },
                ) { followRedirects = false }
            assertFailsWith<IllegalStateException> {
                ApplePushBridge("https://push.example", redirected).active(route, true)
            }
            assertEquals(1, calls)
            redirected.close()
        }

    @Test
    fun aMessageAlreadyAcknowledgedByTheExtensionCanBeSkipped() =
        runTest {
            val http = HttpClient(MockEngine { respond("", HttpStatusCode.NotFound) })
            assertEquals(null, ApplePushBridge("https://push.example", http).message("m".repeat(43)))
            http.close()
        }

    @Test
    fun rejectsInsecureOrAmbiguousOrigins() {
        val http = HttpClient(MockEngine { respond("") })
        listOf(
            "http://push.example",
            "https://user:pass@push.example",
            "https://push.example/path",
            "https://push.example?token=x",
        ).forEach { assertFailsWith<IllegalArgumentException> { ApplePushBridge(it, http) } }
        http.close()
    }
}
