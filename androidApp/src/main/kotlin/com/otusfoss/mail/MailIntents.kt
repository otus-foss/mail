package com.otusfoss.mail

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.otusfoss.mail.app.MailRequests
import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.mailtoRequest
import com.otusfoss.mail.core.messages.domain.parseRecipients
import com.otusfoss.mail.feature.notifications.domain.AlertedMail

/** The login of the message an alert or its button is about. */
internal const val EXTRA_LOGIN_ID = "com.otusfoss.mail.extra.LOGIN_ID"

/** The server's Email id of the message an alert or its button is about. */
internal const val EXTRA_EMAIL_ID = "com.otusfoss.mail.extra.EMAIL_ID"

/** Set by an alert's Reply button: Mail answers the message rather than opening it. */
private const val EXTRA_REPLY = "com.otusfoss.mail.extra.REPLY"

/**
 * Hands what [intent] opens Mail with to [MailRequests]: the message of a tapped alert, a reply to
 * it from the alert's Reply button, or a draft for a `mailto:` link that another app viewed or
 * sent to. An intent the recent apps list gives back asks for nothing new, so it opens nothing
 * again.
 */
internal fun MailRequests.take(intent: Intent) {
    if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
    val loginId = intent.getStringExtra(EXTRA_LOGIN_ID)
    val emailId = intent.getStringExtra(EXTRA_EMAIL_ID)
    val draft = intent.mailtoDraft()
    when {
        loginId != null && emailId != null && intent.replies -> reply(loginId, emailId)
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

private val Intent.replies: Boolean get() = getBooleanExtra(EXTRA_REPLY, false)

/**
 * The alert a Reply button answered, which goes once Mail opens the reply; null for every other
 * intent. Android leaves an alert showing after a button that opens an app.
 */
internal fun Intent.repliedAlert(): AlertedMail? {
    val fromHistory = flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
    if (!replies || fromHistory) return null
    return alertedMail()
}

/** The message an alert's intent names; null when either id is missing. */
private fun Intent.alertedMail(): AlertedMail? {
    val loginId = getStringExtra(EXTRA_LOGIN_ID)
    val emailId = getStringExtra(EXTRA_EMAIL_ID)
    return if (loginId != null && emailId != null) AlertedMail(loginId, emailId) else null
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
        0,
        Intent(this, MainActivity::class.java)
            .setData(alertUri(loginId, emailId))
            .putExtra(EXTRA_LOGIN_ID, loginId)
            .putExtra(EXTRA_EMAIL_ID, emailId)
            .addFlags(IN_PLACE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

/** Opens Mail on a reply to the message [emailId] of [loginId], for its alert's Reply button. */
internal fun Context.replyTo(
    loginId: String,
    emailId: String,
): PendingIntent =
    PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .setData(alertUri(loginId, emailId, "reply"))
            .putExtra(EXTRA_LOGIN_ID, loginId)
            .putExtra(EXTRA_EMAIL_ID, emailId)
            .putExtra(EXTRA_REPLY, true)
            .addFlags(IN_PLACE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

/**
 * Names an alert's message, and its [button] if any, in the intent's data. Android matches
 * PendingIntents by data but not by extras, so each alert's intents need their own, or one alert's
 * button could act on another's message.
 */
internal fun alertUri(
    loginId: String,
    emailId: String,
    button: String? = null,
): Uri =
    listOfNotNull(loginId, emailId, button)
        .fold(Uri.Builder().scheme(ALERT_SCHEME)) { uri, part -> uri.appendPath(part) }
        .build()

private const val ALERT_SCHEME = "otusfoss-alert"

/** Reuses the running activity, so its pages and state stay, and starts one when there is none. */
private const val IN_PLACE =
    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
