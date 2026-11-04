package com.otus.mail

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.otus.mail.feature.notifications.domain.NewMail

/**
 * A button on a new-mail alert that changes the message without opening Mail. [title] is its
 * label's string resource, and [semantic] tells the system what it does, for watches and cars.
 */
internal enum class AlertAction(
    val title: Int,
    val semantic: Int,
) {
    Archive(R.string.notification_archive, NotificationCompat.Action.SEMANTIC_ACTION_ARCHIVE),
    MarkRead(R.string.notification_mark_read, NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ),
    ;

    companion object {
        fun named(name: String?): AlertAction? = entries.firstOrNull { it.name == name }
    }
}

/** The [action] button for the alert about [mail], which [AlertActionReceiver] hears. */
internal fun Context.alertAction(
    mail: NewMail,
    action: AlertAction,
): NotificationCompat.Action {
    val intent =
        Intent(this, AlertActionReceiver::class.java)
            .setAction(action.name)
            .putExtra(EXTRA_LOGIN_ID, mail.loginId)
            .putExtra(EXTRA_EMAIL_ID, mail.emailId)
    // Each alert's buttons need their own request code, or the newest alert's ids would replace the others'.
    val code = "${mail.loginId}/${mail.emailId}/${action.name}".hashCode()
    val pending = PendingIntent.getBroadcast(this, code, intent, PendingIntent.FLAG_IMMUTABLE)
    return NotificationCompat.Action
        .Builder(0, getString(action.title), pending)
        .setSemanticAction(action.semantic)
        .setShowsUserInterface(false)
        .build()
}
