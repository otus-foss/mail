package com.otusfoss.mail

import android.app.Activity
import android.os.Bundle

/**
 * Opens Mail on the message of a tapped alert, or on a reply from the alert's Reply button, and
 * then brings Mail to the front. It is not exported, so only Mail's own alerts reach it: other apps
 * can send intents to the exported [MainActivity], and must not open messages or clear alerts.
 */
class AlertOpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as OtusMailApplication).services.requests.takeAlert(intent)
        // Android leaves an alert showing after a button that opens an app.
        intent.repliedAlert()?.let { MailNotifications(this).clear(listOf(it)) }
        startActivity(mailInPlace())
        finish()
    }
}
