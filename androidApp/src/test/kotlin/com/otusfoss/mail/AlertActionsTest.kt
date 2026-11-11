package com.otusfoss.mail

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.feature.notifications.domain.AlertedMail
import com.otusfoss.mail.feature.notifications.domain.NewMail
import com.otusfoss.mail.feature.notifications.domain.NotificationPreviews
import com.otusfoss.mail.feature.notifications.domain.PreviewChoices
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.time.Instant

/**
 * A tapped alert opens its message, its chosen buttons change it without Mail or open a reply, a
 * button that changes mail asks to unlock, a failed change brings the alert back unless its account
 * is gone, and mailto: intents start drafts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlertActionsTest {
    private val app = ApplicationProvider.getApplicationContext<OtusMailApplication>()
    private val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun mail(
        id: String,
        roles: Set<MailboxRole> = setOf(MailboxRole.Inbox, MailboxRole.Archive, MailboxRole.Trash),
    ) = NewMail("ada", "Ada", id, "Grace", "Order shipped", "On its way", Instant.parse("2026-10-14T08:00:00Z"))
        .copy(mailboxRoles = roles, canSend = true)

    private fun titles(notification: Notification): List<String> = notification.actions.map { it.title.toString() }

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
        assertEquals(listOf("Delete", "Archive", "Mark read"), titles(shown))
        assertNull(shown.publicVersion.actions)
        MailNotifications(app) { PreviewChoices(all = NotificationPreviews.Hidden) }.show(listOf(mail("m2")))
        assertNull(posted().getValue("ada/m2").actions)
    }

    @Test
    fun alertsShowTheChosenButtonsInOrderThatTheAccountCanRun() {
        val chosen = listOf(MailAction.Reply, MailAction.Archive, MailAction.Flag)
        MailNotifications(
            app,
            { chosen },
        ) { PreviewChoices() }.show(listOf(mail("m1", roles = setOf(MailboxRole.Inbox))))
        assertEquals(listOf("Reply", "Flag"), titles(posted().getValue("ada/m1")))
        MailNotifications(app, { emptyList() }) { PreviewChoices() }.show(listOf(mail("m2")))
        assertNull(posted().getValue("ada/m2").actions)
    }

    @Test
    fun replyOpensMailOnAReplyAndClearsTheAlert() {
        MailNotifications(app, { listOf(MailAction.Reply) }) { PreviewChoices() }.show(listOf(mail("m1")))
        val reply = posted().getValue("ada/m1").actions.single()
        val opened = shadowOf(reply.actionIntent).savedIntent
        assertEquals(MainActivity::class.java.name, opened.component?.className)
        assertEquals(AlertedMail("ada", "m1"), opened.repliedAlert())
        assertNull(shadowOf(posted().getValue("ada/m1").contentIntent).savedIntent.repliedAlert())
    }

    @Test
    fun eachAlertsButtonsActOnItsOwnMessage() {
        val chosen = listOf(MailAction.Trash, MailAction.Reply)
        MailNotifications(app, { chosen }) { PreviewChoices() }.show(listOf(mail("m1"), mail("m2")))
        val named =
            listOf("m1", "m2").map { id ->
                posted()
                    .getValue("ada/$id")
                    .actions
                    .map { shadowOf(it.actionIntent).savedIntent.getStringExtra(EXTRA_EMAIL_ID) }
            }
        assertEquals(listOf(listOf("m1", "m1"), listOf("m2", "m2")), named)
    }

    @Test
    fun aFailedChangeBringsTheAlertBackAndSaysSo() {
        MailNotifications(app) { PreviewChoices() }.showFailed(mail("m1"), MailAction.Archive)
        val back = posted().getValue("ada/m1")
        assertEquals("Archive didn't work", back.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString())
        assertEquals(listOf("Delete", "Archive", "Mark read"), titles(back))
        assertEquals(setOf("ada/m1", "ada"), posted().keys)
    }

    @Test
    fun buttonsThatChangeMailAskToUnlockAndReplyDoesNot() {
        val chosen = listOf(MailAction.Trash, MailAction.Archive, MailAction.Reply)
        MailNotifications(app, { chosen }) { PreviewChoices() }.show(listOf(mail("m1")))
        val buttons = posted().getValue("ada/m1").actions
        val unlock = buttons.associate { it.title.toString() to it.isAuthenticationRequired }
        assertEquals(mapOf("Delete" to true, "Archive" to true, "Reply" to false), unlock)
    }

    @Test
    fun aChangeForARemovedAccountStopsWithoutBringingTheAlertBack() {
        val input =
            Data
                .Builder()
                .putString("action", MailAction.Archive.name)
                .putMail(mail("m1"))
                .build()
        val worker = TestListenableWorkerBuilder<AlertActionWorker>(app).setInputData(input).build()
        assertEquals(ListenableWorker.Result.failure(), runBlocking { worker.doWork() })
        assertEquals(emptySet<String>(), posted().keys)
    }

    @Test
    fun aButtonCarriesItsAlertSoItCanComeBack() {
        MailNotifications(app) { PreviewChoices() }.show(listOf(mail("m1")))
        val delete = posted().getValue("ada/m1").actions.first()
        assertEquals(mail("m1"), shadowOf(delete.actionIntent).savedIntent.mail())
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
