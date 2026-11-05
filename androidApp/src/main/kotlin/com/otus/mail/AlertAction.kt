package com.otus.mail

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.notifications.domain.NewMail

/**
 * A button on a new-mail alert, for one of the [MailAction]s the user can choose in Settings. [title]
 * is its label's string resource, and [semantic] tells the system what it does, for watches and
 * cars. Every button but [Reply] changes the message without opening Mail.
 */
internal enum class AlertAction(
    val action: MailAction,
    val title: Int,
    val semantic: Int,
) {
    Trash(MailAction.Trash, R.string.notification_delete, NotificationCompat.Action.SEMANTIC_ACTION_DELETE),
    Archive(MailAction.Archive, R.string.notification_archive, NotificationCompat.Action.SEMANTIC_ACTION_ARCHIVE),
    MarkRead(MailAction.Read, R.string.notification_mark_read, NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ),
    Flag(MailAction.Flag, R.string.notification_flag, NotificationCompat.Action.SEMANTIC_ACTION_NONE),
    Spam(MailAction.Spam, R.string.notification_spam, NotificationCompat.Action.SEMANTIC_ACTION_NONE),

    // Not SEMANTIC_ACTION_REPLY: that promises an inline reply, and this one opens the composer.
    Reply(MailAction.Reply, R.string.notification_reply, NotificationCompat.Action.SEMANTIC_ACTION_NONE),
    ;

    companion object {
        fun named(name: String?): AlertAction? = entries.firstOrNull { it.name == name }

        fun of(action: MailAction): AlertAction? = entries.firstOrNull { it.action == action }
    }
}

/** The [action] button for the alert about [mail]: [AlertActionReceiver] hears it, or Mail opens a reply. */
internal fun Context.alertAction(
    mail: NewMail,
    action: AlertAction,
): NotificationCompat.Action {
    // Each alert's buttons need their own request code, or the newest alert's ids would replace the others'.
    val code = "${mail.loginId}/${mail.emailId}/${action.name}".hashCode()
    val pending =
        if (action == AlertAction.Reply) {
            replyTo(mail.loginId, mail.emailId, code)
        } else {
            val intent = Intent(this, AlertActionReceiver::class.java).setAction(action.name).putMail(mail)
            // Updated, so an alert posted again after a failure carries its current account roles.
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            PendingIntent.getBroadcast(this, code, intent, flags)
        }
    return NotificationCompat.Action
        .Builder(0, getString(action.title), pending)
        .setSemanticAction(action.semantic)
        .setShowsUserInterface(action == AlertAction.Reply)
        .build()
}
