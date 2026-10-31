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
import com.otus.mail.feature.notifications.domain.NewMail
import com.otus.mail.feature.notifications.domain.NotificationPreviews
import com.otus.mail.feature.notifications.domain.PreviewChoices
import com.otus.designsystem.R as DesignR

/**
 * Posts one notification per new message, grouped by account, showing what [previews] allows for
 * its account and addresses: by default the sender, subject, preview and account. On the lock
 * screen the device's setting decides whether such an alert shows in full or only "New mail".
 * [NotificationPreviews.Hidden] says only "New mail" everywhere, without the account.
 */
class MailNotifications(
    context: Context,
    private val previews: () -> PreviewChoices = { AndroidNotificationPreviewStore(context).read() },
) {
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

    fun show(mail: List<NewMail>) {
        // From Android 13 on, posting needs the user's permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (permission != PackageManager.PERMISSION_GRANTED) return
        }
        val choices = previews()
        mail.groupBy { it.loginId }.forEach { (loginId, messages) ->
            val shown = messages.map { choices.forMail(loginId, it.recipients) }
            messages.zip(shown).forEach { (arrival, preview) ->
                manager.notify("$loginId/${arrival.emailId}", MESSAGE_ID, message(arrival, preview))
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
    }.setWhen(mail.receivedAt.toEpochMilliseconds())
        .setShowWhen(true)
        .build()

    private fun detailedMessage(mail: NewMail) =
        base(mail)
            .setContentTitle(mail.sender)
            .setContentText(mail.subject(context))
            .setSubText(mail.accountName)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${mail.subject(context)}\n${mail.preview}"))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(newMail(mail).build())

    private fun summary(
        latest: NewMail,
        shown: NotificationPreviews,
    ) = when (shown) {
        NotificationPreviews.Hidden -> newMail(latest).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        NotificationPreviews.Device -> accountSummary(latest)
    }.setGroupSummary(true)
        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        .build()

    private fun accountSummary(latest: NewMail) =
        base(latest)
            .setContentTitle(latest.accountName)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(newMail(latest).build())

    /** "New mail" alone: a hidden alert, and what a locked device shows when it hides details. */
    private fun newMail(mail: NewMail) = base(mail).setContentTitle(context.getString(R.string.notification_new_mail))

    private fun base(mail: NewMail) =
        NotificationCompat
            .Builder(context, CHANNEL)
            .setSmallIcon(DesignR.drawable.otus_mail_status_icon)
            .setCategory(NotificationCompat.CATEGORY_EMAIL)
            .setGroup("mail/${mail.loginId}")
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

    private fun openApp() =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val CHANNEL = "new_mail"
        const val BACKGROUND_CHANNEL = "background"
        const val BACKGROUND_ID = 3
        const val MESSAGE_ID = 1
        const val SUMMARY_ID = 2
    }
}

private fun NewMail.subject(context: Context) = subject.ifBlank { context.getString(R.string.notification_no_subject) }
