package com.otusfoss.mail

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
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
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        if (waitsForUnlock(Build.VERSION.SDK_INT) { keyguard.isDeviceLocked }) return
        MailNotifications(context).clear(listOf(AlertedMail(mail.loginId, mail.emailId)))
        AlertActionWorker.enqueue(context, action, mail)
    }
}

/**
 * Whether a button press must be ignored because the phone is locked. Android 12 asks to unlock
 * before the press arrives, but older versions send it straight from the lock screen, so the
 * alert stays and the user can press again after unlocking.
 */
internal fun waitsForUnlock(
    sdk: Int,
    locked: () -> Boolean,
): Boolean = sdk < Build.VERSION_CODES.S && locked()
