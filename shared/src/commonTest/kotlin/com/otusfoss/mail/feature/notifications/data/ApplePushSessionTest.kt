package com.otusfoss.mail.feature.notifications.data

import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.core.jmap.login.JmapLogin
import com.otusfoss.core.jmap.login.LoginStore
import com.otusfoss.core.jmap.login.LoginStoreException
import com.otusfoss.mail.app.MailServices
import com.otusfoss.mail.feature.notifications.domain.PushDeliveryStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun aSettledDeviceSendsTheBridgeNothingUntilTheLoginsChange() =
        runTest {
            val host = TestApplePlatform()
            host.saved["enabled"] = "true"
            val paths = mutableListOf<String>()
            val http = fakeBridge(paths)
            val logins = adaLogins()
            val jmap = fakePushServer()
            val session =
                ApplePushSession(host, "https://push.example", http) { MailServices(logins, createClient = { jmap }) }
            assertTrue(session.syncSafely())
            assertEquals(PushDeliveryStatus.Active, session.results["ada"])
            val settled = paths.size
            // An hour in the foreground, checked every 15 seconds.
            repeat(FOREGROUND_CHECKS) { assertTrue(session.checkSafely()) }
            assertEquals(emptyList<String>(), paths.drop(settled))
            logins.remove("ada")
            session.checkSafely()
            assertTrue("DELETE /v1/routes/$FAKE_ROUTE" in paths.drop(settled), "A removed login is revoked")
            http.close()
        }

    private companion object {
        const val FOREGROUND_CHECKS = 240
    }
}
