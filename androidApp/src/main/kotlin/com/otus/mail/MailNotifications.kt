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

/**
 * Posts one notification per new message, grouped by account, showing what [previews] allows: by
 * default nothing on the lock screen and the sender and subject once the device is unlocked. A
 * lock screen that shows an alert at all shows only "New mail", never the account.
 */
class MailNotifications(
    context: Context,
    private val previews: () -> NotificationPreviews = { AndroidNotificationPreviewStore(context).read() },
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
        val shown = previews()
        mail.groupBy { it.loginId }.forEach { (loginId, messages) ->
            messages.forEach { manager.notify("$loginId/${it.emailId}", MESSAGE_ID, message(it, shown)) }
            manager.notify(loginId, SUMMARY_ID, summary(messages.last(), shown))
        }
    }

    private fun message(
        mail: NewMail,
        shown: NotificationPreviews,
    ) = when (shown) {
        NotificationPreviews.Never -> {
            newMail(mail).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }

        NotificationPreviews.Always, NotificationPreviews.Unlocked -> {
            base(mail)
                .setContentTitle(mail.sender)
                .setContentText(subject(mail))
                .setSubText(mail.accountName)
                .setStyle(NotificationCompat.BigTextStyle().bigText("${subject(mail)}\n${mail.preview}"))
                .setVisibility(lockScreen(shown))
                .setPublicVersion(newMail(mail).build())
        }
    }.setWhen(mail.receivedAt.toEpochMilliseconds())
        .setShowWhen(true)
        .build()

    private fun summary(
        latest: NewMail,
        shown: NotificationPreviews,
    ) = when (shown) {
        NotificationPreviews.Never -> newMail(latest).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        else -> base(latest).setContentTitle(latest.accountName).setVisibility(lockScreen(shown))
    }.setGroupSummary(true)
        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        .build()

    /** "New mail" alone, the most a lock screen shows. */
    private fun newMail(mail: NewMail) = base(mail).setContentTitle(context.getString(R.string.notification_new_mail))

    /** Always leaves the lock screen to the device's setting; When unlocked keeps alerts off it. */
    private fun lockScreen(shown: NotificationPreviews) =
        when (shown) {
            NotificationPreviews.Always -> NotificationCompat.VISIBILITY_PRIVATE
            else -> NotificationCompat.VISIBILITY_SECRET
        }

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
