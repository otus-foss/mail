package com.otusfoss.mail

import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.otusfoss.mail.core.text.presentation.markSensitive
import com.otusfoss.mail.feature.notifications.domain.NewMail

/**
 * The Copy code button for the alert about [mail], or null when it gives no code. [AlertActionReceiver]
 * copies the code and marks the message read. The code shows in the alert, so the button asks to
 * unlock on a lock screen as the buttons that change mail do.
 */
internal fun Context.copyCodeAction(mail: NewMail): NotificationCompat.Action? {
    if (mail.code == null) return null
    val intent =
        Intent(this, AlertActionReceiver::class.java)
            .setAction(COPY_CODE)
            .setData(alertUri(mail.loginId, mail.emailId, COPY_CODE))
            .putMail(mail)
    val pending =
        PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    return NotificationCompat.Action
        .Builder(0, COPY_LABEL, pending)
        .setShowsUserInterface(false)
        .setAuthenticationRequired(true)
        .build()
}

/**
 * Puts [code] on the clipboard, out of the copy preview. Android 13 and later say it was copied
 * themselves, so Mail shows nothing more.
 */
internal fun Context.copyCode(code: String) {
    val clip = ClipData.newPlainText(CLIP_LABEL, code).apply { markSensitive() }
    getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

/** The intent action of the Copy code button. */
internal const val COPY_CODE = "CopyCode"

private const val COPY_LABEL = "Copy code"
private const val CLIP_LABEL = "One-time code"
