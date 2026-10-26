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
                .setSmallIcon(android.R.drawable.sym_action_email)
                .setContentTitle(context.getString(R.string.notification_checking))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build(),
        )

    fun show(mail: List<NewMail>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val choices = previews()
        mail.groupBy { it.loginId }.forEach { (loginId, messages) ->
            val shown = messages.map { choices.forMail(loginId, it.recipients) }
            messages.zip(shown).forEach { (it, preview) ->
                manager.notify("$loginId/${it.emailId}", MESSAGE_ID, message(it, preview))
            }
            // The account's name shows only if every message in the group may show its details.
            val summary =
                if (NotificationPreviews.Hidden in
                    shown
                ) {
                    NotificationPreviews.Hidden
                } else {
                    NotificationPreviews.Device
                }
            manager.notify(loginId, SUMMARY_ID, summary(messages.last(), summary))
        }
    }

    private fun message(
        mail: NewMail,
        shown: NotificationPreviews,
    ) = when (shown) {
        NotificationPreviews.Hidden -> {
            newMail(mail).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }

        NotificationPreviews.Device -> {
            base(mail)
                .setContentTitle(mail.sender)
                .setContentText(subject(mail))
                .setSubText(mail.accountName)
                .setStyle(NotificationCompat.BigTextStyle().bigText("${subject(mail)}\n${mail.preview}"))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(newMail(mail).build())
        }
    }.setWhen(mail.receivedAt.toEpochMilliseconds())
        .setShowWhen(true)
        .build()

    private fun summary(
        latest: NewMail,
        shown: NotificationPreviews,
    ) = when (shown) {
        NotificationPreviews.Hidden -> {
            newMail(latest).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }

        NotificationPreviews.Device -> {
            base(latest)
                .setContentTitle(latest.accountName)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(newMail(latest).build())
        }
    }.setGroupSummary(true)
        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        .build()

    /** "New mail" alone: a hidden alert, and what a locked device shows when it hides details. */
    private fun newMail(mail: NewMail) = base(mail).setContentTitle(context.getString(R.string.notification_new_mail))

    private fun base(mail: NewMail) =
        NotificationCompat
            .Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.sym_action_email)
            .setCategory(NotificationCompat.CATEGORY_EMAIL)
            .setGroup("mail/${mail.loginId}")
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

    private fun subject(mail: NewMail) = mail.subject.ifBlank { context.getString(R.string.notification_no_subject) }

    private fun openApp() =
        PendingIntent.getActivity(
            context,
            0,
            Intent(
                context,
                MainActivity::class.java,
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
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
