package com.otusfoss.mail

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.otusfoss.mail.feature.notifications.domain.NewMail
import com.otusfoss.mail.feature.notifications.domain.PreviewChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.time.Instant

/** On Android 11 and older, an alert button pressed on a locked phone leaves the alert and the mail alone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AlertButtonLockTest {
    private val app = ApplicationProvider.getApplicationContext<OtusMailApplication>()
    private val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val mail =
        NewMail("ada", "Ada", "m1", "Grace", "Order shipped", "On its way", Instant.parse("2026-10-14T08:00:00Z"))

    @Before
    fun freshNotificationsAndWork() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    private fun pressMarkRead(locked: Boolean) {
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setIsDeviceLocked(locked)
        MailNotifications(app) { PreviewChoices() }.show(listOf(mail))
        val markRead =
            manager.activeNotifications
                .single { it.tag == "ada/m1" }
                .notification.actions
                .single { it.title.toString() == "Mark read" }
        AlertActionReceiver().onReceive(app, shadowOf(markRead.actionIntent).savedIntent)
    }

    private fun alerts(): Set<String> = manager.activeNotifications.map { it.tag }.toSet()

    private fun queued(): Int =
        WorkManager
            .getInstance(app)
            .getWorkInfosByTag(AlertActionWorker::class.java.name)
            .get()
            .size

    @Test
    fun aButtonOnALockedPhoneKeepsTheAlertAndQueuesNothing() {
        pressMarkRead(locked = true)
        assertEquals(setOf("ada/m1", "ada"), alerts())
        assertEquals(0, queued())
    }

    @Test
    fun aButtonOnAnUnlockedPhoneClearsTheAlertAndQueuesTheChange() {
        pressMarkRead(locked = false)
        assertEquals(emptySet<String>(), alerts())
        assertEquals(1, queued())
    }

    @Test
    fun android12AndLaterActWithoutCheckingTheLock() {
        listOf(31, 35).forEach { sdk ->
            assertFalse(waitsForUnlock(sdk) { error("Android $sdk checked the lock") })
        }
    }
}
