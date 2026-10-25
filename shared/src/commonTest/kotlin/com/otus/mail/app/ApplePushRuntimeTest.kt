package com.otus.mail.app

import com.otus.jmap.JmapClient
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.feature.notifications.data.ApplePushPlatform
import com.otus.mail.feature.notifications.domain.PushDeliveryStatus
import com.otus.mail.testing.stalwartLike
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ApplePushRuntimeTest {
    @Test
    fun verifiesBeforeActivationAndRevokesBeforeForgetting() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val host = TestApplePlatform()
                val paths = mutableListOf<String>()
                val route = "r".repeat(43)
                val message = "m".repeat(43)
                var acknowledged = false
                val http =
                    HttpClient(
                        MockEngine { request ->
                            val path = request.url.encodedPath
                            paths += "${request.method.value} $path"
                            when {
                                request.method == HttpMethod.Post -> {
                                    respond("""{"id":"$route","secret":"secret"}""", HttpStatusCode.Created)
                                }

                                path == "/v1/routes/$route/messages" -> {
                                    respond(if (acknowledged) "[]" else """["$message"]""")
                                }

                                path == "/v1/messages/$message" -> {
                                    respond("ciphertext")
                                }

                                path.endsWith("/messages/$message") -> {
                                    acknowledged = true
                                    respond("", HttpStatusCode.NoContent)
                                }

                                request.method == HttpMethod.Patch -> {
                                    val expected = if (acknowledged) "true" else "false"
                                    assertEquals("{\"active\":$expected}", (request.body as TextContent).text)
                                    respond("", HttpStatusCode.NoContent)
                                }

                                else -> {
                                    respond("", HttpStatusCode.NoContent)
                                }
                            }
                        },
                    ) { followRedirects = false }
                val logins =
                    InMemoryLoginStore().apply {
                        save(
                            JmapLogin("ada", "https://mail.example.org/jmap/session", "ada@example.org", "Ada"),
                            "secret",
                        )
                    }
                val jmap =
                    JmapClient(
                        stalwartLike(api = { body ->
                            when {
                                "PushSubscription/get" in body -> {
                                    """{"methodResponses":[["PushSubscription/get",{"list":[]},"c0"]]}"""
                                }

                                "\"create\"" in body -> {
                                    """{"methodResponses":[["PushSubscription/set",
                        {"created":{"push":{"id":"ps1","expires":"2030-01-01T00:00:00Z"}}},"c0"]]}"""
                                }

                                "\"destroy\"" in body -> {
                                    """{"methodResponses":[["PushSubscription/set",
                        {"destroyed":["ps1"]},"c0"]]}"""
                                }

                                else -> {
                                    """{"methodResponses":[["PushSubscription/set",
                        {"updated":{"ps1":null}},"c0"]]}"""
                                }
                            }
                        }),
                    )
                val runtime =
                    ApplePushRuntime(host, "https://push.example", { push ->
                        MailServices(logins, instantPush = push, createClient = { jmap })
                    }, http)
                runtime.use(runtime.distributors().single())
                assertEquals(PushDeliveryStatus.Active, runtime.statuses()["ada"])
                assertFalse(runtime.periodicChecks)
                assertEquals(0, host.shown)
                runtime.forget("ada")
                assertTrue("DELETE /v1/routes/$route" in paths)
                assertTrue(host.removed)
                assertEquals(emptyMap(), runtime.statuses())
                http.close()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun turningOffUnregistersEvenWhenNoBridgeWasConfigured() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val host = TestApplePlatform()
                val runtime = ApplePushRuntime(host, "", { MailServices() })
                assertTrue(runtime.distributors().isEmpty())
                runtime.use(null)
                assertTrue(host.stopped)
                assertEquals(null, runtime.current())
            } finally {
                Dispatchers.resetMain()
            }
        }
}
