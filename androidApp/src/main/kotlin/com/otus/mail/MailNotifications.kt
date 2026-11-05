package com.otus.mail

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import com.otus.mail.feature.notifications.domain.AlertedMail
import com.otus.mail.feature.notifications.domain.MailAlerts
import com.otus.mail.feature.notifications.domain.NewMail
import com.otus.mail.feature.notifications.domain.NotificationPreviews
import com.otus.mail.feature.notifications.domain.PreviewChoices
import com.otus.designsystem.R as DesignR

/**
 * Posts one notification per new message, grouped by account, showing what [previews] allows for
 * its account and addresses: by default the sender, subject, preview and account. On the lock
 * screen the device's setting decides whether such an alert shows in full or only "New mail".
 * [NotificationPreviews.Hidden] says only "New mail" everywhere, without the account. An account's
 * summary goes once its last message notification is cleared. Tapping an alert opens its message,
 * and an alert that shows the message offers Archive and Mark read; tapping a summary opens Mail.
 */
class MailNotifications(
    context: Context,
    private val previews: () -> PreviewChoices = { AndroidNotificationPreviewStore(context).read() },
) : MailAlerts {
    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)

    fun createChannels() {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat
                    .Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.notification_channel_new_mail))
                    .build(),
                NotificationChannelCompat
                    .Builder(BACKGROUND_CHANNEL, NotificationManagerCompat.IMPORTANCE_MIN)
                    .setName(context.getString(R.string.notification_channel_background))
                    .build(),
            ),
        )
    }

    /** Before Android 12, expedited work runs as a foreground service, which must show this. */
    fun backgroundWork(): ForegroundInfo =
        ForegroundInfo(
            BACKGROUND_ID,
            NotificationCompat
                .Builder(context, BACKGROUND_CHANNEL)
                .setSmallIcon(DesignR.drawable.otus_mail_status_icon)
                .setContentTitle(context.getString(R.string.notification_checking))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build(),
        )

    override fun showing(): Set<AlertedMail> =
        manager.activeNotifications
            .filter { it.id == MESSAGE_ID }
            .mapNotNull { it.tag }
            .map { AlertedMail(it.substringBeforeLast('/'), it.substringAfterLast('/')) }
            .toSet()

    override fun clear(mail: List<AlertedMail>) {
        // Read before cancelling: the system removes notifications a moment after the call.
        val remaining = (showing() - mail.toSet()).map { it.loginId }.toSet()
        mail.forEach { manager.cancel(it.tag, MESSAGE_ID) }
        mail
            .map { it.loginId }
            .distinct()
            .filterNot { it in remaining }
            .forEach { manager.cancel(it, SUMMARY_ID) }
    }

    override fun show(mail: List<NewMail>) {
        // From Android 13 on, posting needs the user's permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (permission != PackageManager.PERMISSION_GRANTED) return
        }
        val choices = previews()
        mail.groupBy { it.loginId }.forEach { (loginId, messages) ->
            val shown = messages.map { choices.forMail(loginId, it.recipients) }
            messages.zip(shown).forEach { (arrival, preview) ->
                manager.notify(AlertedMail(loginId, arrival.emailId).tag, MESSAGE_ID, message(arrival, preview))
            }
            // The account's name shows only if every message in the group may show its details.
            val summary =
                if (NotificationPreviews.Hidden in shown) NotificationPreviews.Hidden else NotificationPreviews.Device
            manager.notify(loginId, SUMMARY_ID, summary(messages.last(), summary))
        }
    }

    private fun message(
        mail: NewMail,
        shown: NotificationPreviews,
    ) = when (shown) {
        NotificationPreviews.Hidden -> newMail(mail).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        NotificationPreviews.Device -> detailedMessage(mail)
    }.setContentIntent(context.openMessage(mail.loginId, mail.emailId))
        .setWhen(mail.receivedAt.toEpochMilliseconds())
        .setShowWhen(true)
        .build()

    private fun detailedMessage(mail: NewMail) =
        context
            .mailAlert(mail)
            .setContentTitle(mail.sender)
            .setContentText(mail.subject(context))
            .setSubText(mail.accountName)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${mail.subject(context)}\n${mail.preview}"))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(newMail(mail).build())
            // Only an alert that shows the message offers to change it; the locked version has no buttons.
            .addAction(context.alertAction(mail, AlertAction.Archive))
            .addAction(context.alertAction(mail, AlertAction.MarkRead))

    private fun summary(
        latest: NewMail,
        shown: NotificationPreviews,
    ) = when (shown) {
        NotificationPreviews.Hidden -> newMail(latest).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        NotificationPreviews.Device -> accountSummary(latest)
    }.setContentIntent(context.openApp())
        .setGroupSummary(true)
        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        .build()

    private fun accountSummary(latest: NewMail) =
        context
            .mailAlert(latest)
            .setContentTitle(latest.accountName)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(newMail(latest).build())

    /** "New mail" alone: a hidden alert, and what a locked device shows when it hides details. */
    private fun newMail(mail: NewMail) =
        context
            .mailAlert(mail)
            .setContentTitle(context.getString(R.string.notification_new_mail))

    private companion object {
        const val BACKGROUND_CHANNEL = "background"
        const val BACKGROUND_ID = 3
        const val MESSAGE_ID = 1
        const val SUMMARY_ID = 2
    }
}

private val AlertedMail.tag get() = "$loginId/$emailId"

private const val CHANNEL = "new_mail"

/** The parts every alert about [mail] shares: its channel, icon, and account group. */
private fun Context.mailAlert(mail: NewMail) =
    NotificationCompat
        .Builder(this, CHANNEL)
        .setSmallIcon(DesignR.drawable.otus_mail_status_icon)
        .setCategory(NotificationCompat.CATEGORY_EMAIL)
        .setGroup("mail/${mail.loginId}")
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)

private fun Context.openApp() =
    PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

private fun NewMail.subject(context: Context) = subject.ifBlank { context.getString(R.string.notification_no_subject) }
