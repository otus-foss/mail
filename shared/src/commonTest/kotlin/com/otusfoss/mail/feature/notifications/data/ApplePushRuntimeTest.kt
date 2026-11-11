package com.otusfoss.mail.feature.notifications.data

import com.otusfoss.mail.app.MailPlatform
import com.otusfoss.mail.app.MailServices
import com.otusfoss.mail.feature.notifications.domain.PushDeliveryStatus
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
                val http = fakeBridge(paths)
                val logins = adaLogins()
                val jmap = fakePushServer()
                val runtime =
                    ApplePushRuntime(host, "https://push.example", { push ->
                        MailServices(logins, host = MailPlatform(instantPush = push), createClient = { jmap })
                    }, http)
                runtime.use(runtime.distributors().single())
                assertEquals(PushDeliveryStatus.Active, runtime.statuses()["ada"])
                assertFalse(runtime.periodicChecks)
                assertEquals(0, host.shown)
                runtime.forget("ada")
                assertTrue("DELETE /v1/routes/$FAKE_ROUTE" in paths)
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
