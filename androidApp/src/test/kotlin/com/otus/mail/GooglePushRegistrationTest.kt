package com.otus.mail

import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.otus.jmap.JmapException
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.push.PushEndpoint
import com.otus.mail.app.MailServices
import com.otus.mail.feature.notifications.domain.MailPush
import com.otus.mail.feature.notifications.domain.PushDeliveryStatus
import com.otus.storage.StringMapStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.keys.KeyManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GooglePushRegistrationTest {
    private val app = ApplicationProvider.getApplicationContext<OtusMailApplication>()
    private val broadcasts = mutableListOf<Intent>()

    // Native delivery needs Play services. Keep these tests at the connector broadcast boundary.
    private val context =
        object : ContextWrapper(app) {
            override fun sendBroadcast(intent: Intent) {
                broadcasts += intent
            }

            override fun sendBroadcast(
                intent: Intent,
                receiverPermission: String?,
            ) {
                broadcasts += intent
            }

            override fun sendBroadcast(
                intent: Intent,
                receiverPermission: String?,
                options: Bundle?,
            ) {
                broadcasts += intent
            }
        }
    private val login = JmapLogin("ada", "https://example.org/jmap/session", "ada", "Ada")
    private val state =
        object : StringMapStore {
            var records = emptyMap<String, String>()

            override fun read() = records

            override fun write(values: Map<String, String>) {
                records = values
            }
        }
    private val subscriptions = MailServices().pushSubscriptions(state, "test-device", MailPush.Changes)
    private var key: String? = KEY
    private val registration by lazy { registrar { key } }

    @Test
    fun googleIsAvailableOnlyWithPlayServices() =
        runBlocking {
            assertFalse(app.instantPush.distributors().any { it.isGoogle })
            shadowOf(app.packageManager).installPackage(PackageInfo().apply { packageName = "com.google.android.gms" })
            val google = app.instantPush.distributors().first()
            assertTrue(google.isGoogle)
            assertEquals("Google Play services", google.name)
            assertEquals(app.packageName, google.id)
        }

    @Test
    fun registrationUsesTheServersKeyAndLoginId() =
        runBlocking {
            UnifiedPush.saveDistributor(context, app.packageName)
            key = "$KEY="
            registration.register(login, app.packageName, retry = true)
            val request = requests().single()
            assertEquals(KEY, request.getStringExtra("vapid"))
            assertEquals(app.packageName, request.`package`)
            assertEquals("Otus Mail: Ada", request.getStringExtra("message"))
            registration.register(login, app.packageName, retry = false)
            assertEquals(1, requests().size)
        }

    @Test
    fun missingOrMalformedKeysDoNotAttemptGoogleRegistration() =
        runBlocking {
            UnifiedPush.saveDistributor(context, app.packageName)
            key = null
            registration.register(login, app.packageName, retry = true)
            assertEquals(PushDeliveryStatus.VapidRequired, registration.problems()[login.id])
            key = "bad-key"
            registration.register(login, app.packageName, retry = true)
            assertEquals(PushDeliveryStatus.InvalidKey, registration.problems()[login.id])
            assertTrue(requests().isEmpty())
        }

    @Test
    fun rotatingTheKeyReplacesRegistrationAndClearsTheOldEndpoint() =
        runBlocking {
            UnifiedPush.saveDistributor(context, app.packageName)
            registration.register(login, app.packageName, retry = true)
            val token = requests().single().getStringExtra("token")
            subscriptions.setEndpoint(login.id, PushEndpoint("https://push.example.org/old", PushKeys("key", "auth")))
            key = OTHER_KEY
            registration.register(login, app.packageName, retry = false)
            assertNotEquals(token, requests().last().getStringExtra("token"))
            assertEquals(OTHER_KEY, requests().last().getStringExtra("vapid"))
            assertEquals(com.otus.jmap.push.PushStatus.Off, subscriptions.status()[login.id])
        }

    @Test
    fun failuresSurviveRestartAndRetryOnlyOnTheNextScheduledRun() =
        runBlocking {
            UnifiedPush.saveDistributor(context, app.packageName)
            registration.register(login, app.packageName, retry = true)
            registration.failed(login.id, PushDeliveryStatus.RegistrationFailed)
            val restarted = registrar { key }
            assertEquals(PushDeliveryStatus.RegistrationFailed, restarted.problems()[login.id])
            restarted.register(login, app.packageName, retry = false)
            assertEquals(1, requests().size)
            restarted.register(login, app.packageName, retry = true)
            assertEquals(2, requests().size)
            restarted.connected(login.id)
            assertTrue(restarted.problems().isEmpty())
        }

    @Test
    fun oneServersFailureDoesNotBlockAnotherAccount() =
        runBlocking {
            UnifiedPush.saveDistributor(context, app.packageName)
            val failing = registrar { if (it.id == login.id) throw JmapException.Unauthorized() else KEY }
            failing.register(login, app.packageName, retry = true)
            failing.register(login.copy(id = "bob", name = "Bob"), app.packageName, retry = true)
            assertEquals(PushDeliveryStatus.Unavailable, failing.problems()[login.id])
            assertEquals(1, requests().size)
        }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotReportedAsRegistrationFailure(): Unit =
        runBlocking {
            registrar { throw CancellationException() }
                .register(login, app.packageName, retry = true)
        }

    @Test
    fun externalDistributorsStillWorkWithoutVapid() =
        runBlocking {
            UnifiedPush.saveDistributor(context, "io.heckel.ntfy")
            val external = registrar { error("External push must not fetch a Google key") }
            external.register(login, "io.heckel.ntfy", retry = true)
            assertEquals("io.heckel.ntfy", requests().single().`package`)
            assertFalse(requests().single().hasExtra("vapid"))
        }

    @After
    fun removeRegistrations() {
        // The connector keeps its database in a singleton across Robolectric application instances.
        UnifiedPush.removeDistributor(context, testKeys)
    }

    private val testKeys =
        object : KeyManager {
            override fun exists(instance: String) = true

            override fun generate(instance: String) = Unit

            override fun delete(instance: String) = Unit

            override fun getPublicKeySet(instance: String) = null

            override fun decrypt(
                instance: String,
                sealed: ByteArray,
            ) = null
        }

    // Robolectric has no Android keystore. These tests inspect registration, not encryption.
    private fun registrar(key: suspend (JmapLogin) -> String?): AndroidPushRegistration =
        AndroidPushRegistration(context, subscriptions, key, testKeys)

    private fun requests(): List<Intent> =
        broadcasts.filter { it.action == "org.unifiedpush.android.distributor.REGISTER" }

    private companion object {
        const val KEY = "BJVlg_p7GZr_ZluA2ace8aWj8dXVG6hB5L19VhMX3lbVd3c8IqrziiHVY3ERNVhB9Jje5HNZQI4nUOtF_XkUIyI"
        const val OTHER_KEY = "BJFlg_p7GZr_ZluA2ace8aWj8dXVG6hB5L19VhMX3lbVd3c8IqrziiHVY3ERNVhB9Jje5HNZQI4nUOtF_XkUIyI"
    }
}
