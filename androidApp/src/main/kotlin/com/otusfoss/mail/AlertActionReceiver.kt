package com.otusfoss.mail

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.otusfoss.mail.feature.notifications.domain.AlertedMail
import com.otusfoss.mail.feature.notifications.presentation.opensMailFromAlert

/**
 * Hears an alert's button that changes its message without opening Mail: the alert goes at once,
 * with its account's summary when it was the last, and [AlertActionWorker] tells the server.
 */
class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = alertButton(intent.action)?.takeIf { !it.opensMailFromAlert } ?: return
        val mail = intent.mail() ?: return
        MailNotifications(context).clear(listOf(AlertedMail(mail.loginId, mail.emailId)))
        AlertActionWorker.enqueue(context, action, mail)
    }
}
