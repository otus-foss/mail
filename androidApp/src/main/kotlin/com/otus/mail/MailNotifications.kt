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

/**
 * Posts one notification per new message, grouped by account. The lock screen shows only
 * "New mail" and the account until the device is unlocked.
 */
class MailNotifications(
    context: Context,
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
        mail.groupBy { it.loginId }.forEach { (loginId, messages) ->
            messages.forEach { manager.notify("$loginId/${it.emailId}", MESSAGE_ID, message(it)) }
            manager.notify(loginId, SUMMARY_ID, summary(messages.last()))
        }
    }

    private fun message(mail: NewMail) =
        base(mail)
            .setContentTitle(mail.sender)
            .setContentText(subject(mail))
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOf(subject(mail), mail.preview).joinToString("\n")))
            .setWhen(mail.receivedAt.toEpochMilliseconds())
            .setShowWhen(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                base(mail)
                    .setContentTitle(context.getString(R.string.notification_new_mail))
                    .setContentText(mail.accountName)
                    .build(),
            ).build()

    private fun summary(latest: NewMail) =
        base(latest)
            .setContentTitle(latest.accountName)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .build()

    private fun base(mail: NewMail) =
        NotificationCompat
            .Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.sym_action_email)
            .setCategory(NotificationCompat.CATEGORY_EMAIL)
            .setSubText(mail.accountName)
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
