package com.otus.mail

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.otus.mail.app.MailRequests
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.mailtoRequest
import com.otus.mail.feature.inbox.domain.parseRecipients

/** The login of the message an alert or its button is about. */
internal const val EXTRA_LOGIN_ID = "com.otus.mail.extra.LOGIN_ID"

/** The server's Email id of the message an alert or its button is about. */
internal const val EXTRA_EMAIL_ID = "com.otus.mail.extra.EMAIL_ID"

/**
 * Hands what [intent] opens Mail with to [MailRequests]: the message of a tapped alert, or a draft
 * for a `mailto:` link that another app viewed or sent to. An intent the recent apps list gives
 * back asks for nothing new, so it opens nothing again.
 */
internal fun MailRequests.take(intent: Intent) {
    if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
    val loginId = intent.getStringExtra(EXTRA_LOGIN_ID)
    val emailId = intent.getStringExtra(EXTRA_EMAIL_ID)
    val draft = intent.mailtoDraft()
    when {
        loginId != null && emailId != null -> openMessage(loginId, emailId)
        draft != null -> compose(draft)
    }
}

/**
 * The draft a `mailto:` VIEW or SENDTO intent asks for. Apps often leave the link bare and put the
 * recipients, subject, and text in the intent's extras instead, so those fill in what the link
 * leaves out.
 */
internal fun Intent.mailtoDraft(): ComposeRequest.New? {
    val opensLink = action == Intent.ACTION_VIEW || action == Intent.ACTION_SENDTO
    val link = dataString?.takeIf { opensLink }?.let(::mailtoRequest) ?: return null
    return link.copy(
        to = link.to + addresses(Intent.EXTRA_EMAIL),
        cc = link.cc + addresses(Intent.EXTRA_CC),
        bcc = link.bcc + addresses(Intent.EXTRA_BCC),
        subject = link.subject.ifEmpty { getStringExtra(Intent.EXTRA_SUBJECT).orEmpty() },
        body = link.body.ifEmpty { getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty() },
    )
}

private fun Intent.addresses(extra: String): List<MailSender> =
    getStringArrayExtra(extra).orEmpty().flatMap(::parseRecipients)

/**
 * Opens Mail on the message [emailId] of [loginId]. An open Mail comes to the front and takes the
 * request in place; a closed one starts with it.
 */
internal fun Context.openMessage(
    loginId: String,
    emailId: String,
): PendingIntent =
    PendingIntent.getActivity(
        this,
        // Each alert needs its own request code, or the newest alert's ids would replace the others'.
        "$loginId/$emailId".hashCode(),
        Intent(this, MainActivity::class.java)
            .putExtra(EXTRA_LOGIN_ID, loginId)
            .putExtra(EXTRA_EMAIL_ID, emailId)
            .addFlags(IN_PLACE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

/** Reuses the running activity, so its pages and state stay, and starts one when there is none. */
private const val IN_PLACE =
    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
