package com.otus.mail

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.presentation.actions.buttonLabel
import com.otus.mail.feature.inbox.presentation.actions.opensMailFromAlert
import com.otus.mail.feature.notifications.domain.NewMail

/** The [action] button for the alert about [mail]: [AlertActionReceiver] hears it, or Mail opens a reply. */
internal fun Context.alertAction(
    mail: NewMail,
    action: MailAction,
): NotificationCompat.Action {
    // Each alert's buttons need their own request code, or the newest alert's ids would replace the others'.
    val code = "${mail.loginId}/${mail.emailId}/${action.name}".hashCode()
    val pending =
        if (action.opensMailFromAlert) {
            replyTo(mail.loginId, mail.emailId, code)
        } else {
            val intent = Intent(this, AlertActionReceiver::class.java).setAction(action.name).putMail(mail)
            // Updated, so an alert posted again after a failure carries its current account roles.
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            PendingIntent.getBroadcast(this, code, intent, flags)
        }
    return NotificationCompat.Action
        .Builder(0, action.buttonLabel(), pending)
        .setSemanticAction(action.semantic)
        .setShowsUserInterface(action.opensMailFromAlert)
        .build()
}

/**
 * The button an intent or queued change names. Alerts and changes from before buttons were named
 * by their action call Mark read "MarkRead", and the system keeps them across an update.
 */
internal fun alertButton(name: String?): MailAction? =
    MailAction.entries.firstOrNull { it.name == name } ?: MailAction.Read.takeIf { name == "MarkRead" }

/** What the button does, for watches and cars. */
private val MailAction.semantic: Int
    get() =
        when (this) {
            MailAction.Trash -> NotificationCompat.Action.SEMANTIC_ACTION_DELETE

            MailAction.Archive -> NotificationCompat.Action.SEMANTIC_ACTION_ARCHIVE

            MailAction.Read -> NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ

            // Not SEMANTIC_ACTION_REPLY for Reply: that promises an inline reply, and this one opens the composer.
            else -> NotificationCompat.Action.SEMANTIC_ACTION_NONE
        }
