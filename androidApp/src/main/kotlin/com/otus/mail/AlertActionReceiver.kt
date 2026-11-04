package com.otus.mail

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.otus.mail.feature.notifications.domain.AlertedMail

/**
 * Hears an alert's Archive or Mark read button: the alert goes at once, with its account's summary
 * when it was the last, and [AlertActionWorker] tells the server.
 */
class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = AlertAction.named(intent.action)
        val loginId = intent.getStringExtra(EXTRA_LOGIN_ID)
        val emailId = intent.getStringExtra(EXTRA_EMAIL_ID)
        if (action == null || loginId == null || emailId == null) return
        MailNotifications(context).clear(listOf(AlertedMail(loginId, emailId)))
        AlertActionWorker.enqueue(context, action, loginId, emailId)
    }
}
