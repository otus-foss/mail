package com.otus.mail

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.notifications.domain.NewMail
import com.otus.mail.feature.notifications.domain.NotificationPreviews
import com.otus.mail.feature.notifications.domain.PreviewChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.time.Instant

/** A tapped alert opens its message, its buttons change it without Mail, and mailto: intents start drafts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlertActionsTest {
    private val app = ApplicationProvider.getApplicationContext<OtusMailApplication>()
    private val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun mail(id: String) =
        NewMail("ada", "Ada", id, "Grace", "Order shipped", "On its way", Instant.parse("2026-10-14T08:00:00Z"))

    private fun posted(): Map<String, Notification> =
        manager.activeNotifications.associate { it.tag to it.notification }

    @Before
    fun allowNotifications() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun tappingAnAlertOpensItsMessageAndTheSummaryOpensMail() {
        MailNotifications(app) { PreviewChoices() }.show(listOf(mail("m1"), mail("m2")))
        val tapped = shadowOf(posted().getValue("ada/m1").contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, tapped.component?.className)
        assertEquals("ada", tapped.getStringExtra(EXTRA_LOGIN_ID))
        assertEquals("m1", tapped.getStringExtra(EXTRA_EMAIL_ID))
        val other = shadowOf(posted().getValue("ada/m2").contentIntent).savedIntent
        assertEquals("m2", other.getStringExtra(EXTRA_EMAIL_ID))
        val summary = shadowOf(posted().getValue("ada").contentIntent).savedIntent
        assertNull(summary.getStringExtra(EXTRA_EMAIL_ID))
    }

    @Test
    fun onlyAnAlertThatShowsTheMessageOffersButtons() {
        MailNotifications(app) { PreviewChoices() }.show(listOf(mail("m1")))
        val shown = posted().getValue("ada/m1")
        assertEquals(listOf("Archive", "Mark read"), shown.actions.map { it.title.toString() })
        assertNull(shown.publicVersion.actions)
        MailNotifications(app) { PreviewChoices(all = NotificationPreviews.Hidden) }.show(listOf(mail("m2")))
        assertNull(posted().getValue("ada/m2").actions)
    }

    @Test
    fun aButtonClearsItsAlertAndQueuesTheChange() {
        MailNotifications(app) { PreviewChoices() }.show(listOf(mail("m1"), mail("m2")))
        val markRead = posted().getValue("ada/m1").actions.single { it.title.toString() == "Mark read" }
        AlertActionReceiver().onReceive(app, shadowOf(markRead.actionIntent).savedIntent)
        assertEquals(setOf("ada/m2", "ada"), posted().keys)
        val queued = WorkManager.getInstance(app).getWorkInfosByTag(AlertActionWorker::class.java.name).get()
        assertEquals(1, queued.size)
    }

    @Test
    fun aMailtoIntentTakesTheLinkAndFillsTheRestFromExtras() {
        val intent =
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:a@example.com?subject=Hi"))
                .putExtra(Intent.EXTRA_CC, arrayOf("b@example.com"))
                .putExtra(Intent.EXTRA_SUBJECT, "Not this")
                .putExtra(Intent.EXTRA_TEXT, "Hello")
        val expected =
            ComposeRequest.New(
                to = listOf(MailSender("", "a@example.com")),
                cc = listOf(MailSender("", "b@example.com")),
                subject = "Hi",
                body = "Hello",
            )
        assertEquals(expected, intent.mailtoDraft())
        assertNull(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).mailtoDraft())
        assertNull(Intent(Intent.ACTION_SEND, Uri.parse("mailto:a@example.com")).mailtoDraft())
    }
}
