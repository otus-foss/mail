package com.otus.mail

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.otus.mail.feature.notifications.domain.NewMail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.time.Instant

/** New mail becomes one notification per message plus a summary for each account. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MailNotificationsTest {
    private val app = ApplicationProvider.getApplicationContext<OtusMailApplication>()
    private val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun mail(
        id: String,
        login: String = "ada",
        subject: String = "Order shipped",
    ) = NewMail(login, "Ada", id, "Grace", subject, "Your parcel is on its way", Instant.parse("2026-10-14T08:00:00Z"))

    @Test
    fun eachMessageIsShownAndGroupedByAccount() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        MailNotifications(app).show(listOf(mail("m1"), mail("m2", subject = " "), mail("m3", login = "bob")))
        val posted = manager.activeNotifications.associate { it.tag to it.notification }
        assertEquals(setOf("ada/m1", "ada/m2", "bob/m3", "ada", "bob"), posted.keys)
        val first = posted.getValue("ada/m1")
        assertEquals("Grace", first.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Order shipped", first.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(
            "(No subject)",
            posted
                .getValue("ada/m2")
                .extras
                .getCharSequence(Notification.EXTRA_TEXT)
                .toString(),
        )
        assertEquals("New mail", first.publicVersion.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(Notification.VISIBILITY_PRIVATE, first.visibility)
        assertEquals("mail/ada", first.group)
        assertTrue(posted.getValue("ada").flags and Notification.FLAG_GROUP_SUMMARY != 0)
    }

    @Test
    fun nothingIsPostedWithoutPermission() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        MailNotifications(app).show(listOf(mail("m1")))
        assertEquals(0, manager.activeNotifications.size)
    }

    @Test
    fun theAppSchedulesTheBackgroundCheckAndChannel() {
        val work = WorkManager.getInstance(app).getWorkInfosForUniqueWork("new-mail-check").get()
        assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
        assertEquals("New mail", manager.getNotificationChannel("new_mail").name)
    }
}
