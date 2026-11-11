package com.otusfoss.mail

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.WorkManager
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.feature.notifications.domain.NewMail
import com.otusfoss.mail.feature.notifications.domain.PreviewChoices
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.time.Instant

/**
 * Alert buttons and their queued changes outlive an update, so this build reads what the build
 * before it wrote: separate extras, and "MarkRead" for Mark read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlertMailExtrasTest {
    private val app = ApplicationProvider.getApplicationContext<OtusMailApplication>()

    private val mail =
        NewMail(
            "ada",
            "Ada",
            "m1",
            "Grace",
            "Order shipped",
            "On its way",
            Instant.parse("2026-10-14T08:00:00Z"),
            recipients = listOf("ada@example.org"),
            mailboxRoles = setOf(MailboxRole.Inbox, MailboxRole.Archive),
            canSend = true,
        )

    private fun legacyIntent() =
        Intent(app, AlertActionReceiver::class.java)
            .setAction("MarkRead")
            .putExtra(EXTRA_LOGIN_ID, "ada")
            .putExtra(EXTRA_EMAIL_ID, "m1")
            .putExtra(LEGACY_ACCOUNT, "Ada")
            .putExtra(LEGACY_SENDER, "Grace")
            .putExtra(LEGACY_SUBJECT, "Order shipped")
            .putExtra(LEGACY_PREVIEW, "On its way")
            .putExtra(LEGACY_RECEIVED, mail.receivedAt.toEpochMilliseconds())
            .putExtra(LEGACY_RECIPIENTS, arrayOf("ada@example.org"))
            .putExtra(LEGACY_ROLES, arrayOf("inbox", "archive"))
            .putExtra(LEGACY_CAN_SEND, true)

    @Test
    fun aButtonFromTheBuildBeforeReadsItsSeparateExtras() {
        assertEquals(mail, legacyIntent().mail())
        assertEquals(MailAction.Read, alertButton(legacyIntent().action))
    }

    @Test
    fun aButtonFromTheBuildBeforeClearsItsAlertAndQueuesTheChange() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        MailNotifications(app) { PreviewChoices() }.show(listOf(mail))
        AlertActionReceiver().onReceive(app, legacyIntent())
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals(emptyList<String>(), manager.activeNotifications.map { it.tag })
        val queued = WorkManager.getInstance(app).getWorkInfosByTag(AlertActionWorker::class.java.name).get()
        assertEquals(1, queued.size)
    }

    @Test
    fun aChangeQueuedByTheBuildBeforeReadsItsSeparateFields() {
        val input =
            Data
                .Builder()
                .putString(EXTRA_LOGIN_ID, "ada")
                .putString(EXTRA_EMAIL_ID, "m1")
                .putString(LEGACY_ACCOUNT, "Ada")
                .putString(LEGACY_SENDER, "Grace")
                .putString(LEGACY_SUBJECT, "Order shipped")
                .putString(LEGACY_PREVIEW, "On its way")
                .putLong(LEGACY_RECEIVED, mail.receivedAt.toEpochMilliseconds())
                .putStringArray(LEGACY_RECIPIENTS, arrayOf("ada@example.org"))
                .putStringArray(LEGACY_ROLES, arrayOf("inbox", "archive"))
                .putBoolean(LEGACY_CAN_SEND, true)
                .build()
        assertEquals(mail, input.mail())
    }

    @Test
    fun theAlertsJsonReadsWithAFieldThisBuildDoesNotKnow() {
        val written = mail.encoded().replaceFirst("{", "{\"later\":1,")
        val input =
            Data
                .Builder()
                .putString(EXTRA_LOGIN_ID, "ada")
                .putString(EXTRA_EMAIL_ID, "m1")
                .putString("com.otusfoss.mail.extra.MAIL", written)
                .build()
        assertEquals(mail, input.mail())
        assertEquals(
            mail,
            Data
                .Builder()
                .putMail(mail)
                .build()
                .mail(),
        )
    }
}
