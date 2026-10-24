package com.otus.mail.app

import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.feature.notifications.data.ApplePushRoute
import com.otus.mail.feature.notifications.data.AppleRegistration
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ApplePushSessionTest {
    private fun savedRoute() =
        Json.encodeToString(
            mapOf(
                "ada" to AppleRegistration(ApplePushRoute("r".repeat(43), "secret"), "key-id", "{}", "old-token"),
            ),
        )

    @Test
    fun lockedLoginStorageDoesNotRevokeAnyRoute() =
        runTest {
            val host = TestApplePlatform()
            val original = savedRoute()
            host.saved["routes"] = original
            host.saved["enabled"] = "true"
            val locked =
                object : LoginStore by InMemoryLoginStore() {
                    override fun logins(): List<JmapLogin> = throw LoginStoreException("locked")
                }
            val http = HttpClient(MockEngine { error("No bridge request while account storage is locked") })
            val session = ApplePushSession(host, "https://push.example", http) { MailServices(locked) }
            assertFalse(session.syncSafely())
            assertEquals(original, host.saved["routes"])
            assertFalse(host.removed)
            http.close()
        }

    @Test
    fun failedRevocationKeepsTheCapabilityForRetry() =
        runTest {
            val host = TestApplePlatform()
            val original = savedRoute()
            host.saved["routes"] = original
            host.saved["enabled"] = "false"
            val http = HttpClient(MockEngine { respond("", HttpStatusCode.ServiceUnavailable) })
            val session = ApplePushSession(host, "https://push.example", http) { MailServices() }
            assertFalse(session.syncSafely())
            assertEquals(original, host.saved["routes"])
            assertFalse(host.removed)
            http.close()
        }
}
