package com.otus.mail

import android.content.Intent
import androidx.work.Data
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.feature.notifications.domain.NewMail
import kotlin.time.Instant

// What a button carries about its alert, so a change that fails can post the same alert again.
private const val EXTRA_MAIL = "com.otus.mail.extra.MAIL"

/** This intent with [mail]'s alert in its extras. */
internal fun Intent.putMail(mail: NewMail): Intent =
    putExtra(EXTRA_LOGIN_ID, mail.loginId)
        .putExtra(EXTRA_EMAIL_ID, mail.emailId)
        .putExtra(EXTRA_MAIL, mail.encoded())

/** The alert [putMail], or a build before it, put in this intent; null without its ids. */
internal fun Intent.mail(): NewMail? =
    AlertFields(::getStringExtra, { getLongExtra(it, 0) }, ::getStringArrayExtra) { getBooleanExtra(it, false) }
        .mail()

/** [mail]'s alert as work input, next to what [Data.Builder] already holds. */
internal fun Data.Builder.putMail(mail: NewMail): Data.Builder =
    putString(EXTRA_LOGIN_ID, mail.loginId)
        .putString(EXTRA_EMAIL_ID, mail.emailId)
        .putString(EXTRA_MAIL, mail.encoded())

/** The alert [Data.Builder.putMail], or a build before it, put in this input; null without its ids. */
internal fun Data.mail(): NewMail? =
    AlertFields(::getString, { getLong(it, 0) }, ::getStringArray) { getBoolean(it, false) }
        .mail()

/** An intent's extras or a work input, read by key. */
private class AlertFields(
    val string: (String) -> String?,
    val long: (String) -> Long,
    val strings: (String) -> Array<String>?,
    val flag: (String) -> Boolean,
)

/**
 * The alert as JSON, else as the separate extras that builds before it wrote. The system keeps
 * an alert's buttons, and WorkManager its queued changes, across an update, so both forms reach
 * this build.
 */
private fun AlertFields.mail(): NewMail? = NewMail.decoded(string(EXTRA_MAIL)) ?: legacyMail()

private fun AlertFields.legacyMail(): NewMail? {
    val loginId = string(EXTRA_LOGIN_ID)
    val emailId = string(EXTRA_EMAIL_ID)
    if (loginId == null || emailId == null) return null
    return NewMail(
        loginId = loginId,
        accountName = string(LEGACY_ACCOUNT).orEmpty(),
        emailId = emailId,
        sender = string(LEGACY_SENDER).orEmpty(),
        subject = string(LEGACY_SUBJECT).orEmpty(),
        preview = string(LEGACY_PREVIEW).orEmpty(),
        receivedAt = Instant.fromEpochMilliseconds(long(LEGACY_RECEIVED)),
        recipients = strings(LEGACY_RECIPIENTS).orEmpty().toList(),
        mailboxRoles = strings(LEGACY_ROLES).orEmpty().mapNotNull(MailboxRole::of).toSet(),
        canSend = flag(LEGACY_CAN_SEND),
    )
}

// The extras builds before EXTRA_MAIL wrote, beside the ids; the roles are JMAP role names.
internal const val LEGACY_ACCOUNT = "com.otus.mail.extra.ACCOUNT"
internal const val LEGACY_SENDER = "com.otus.mail.extra.SENDER"
internal const val LEGACY_SUBJECT = "com.otus.mail.extra.SUBJECT"
internal const val LEGACY_PREVIEW = "com.otus.mail.extra.PREVIEW"
internal const val LEGACY_RECEIVED = "com.otus.mail.extra.RECEIVED"
internal const val LEGACY_RECIPIENTS = "com.otus.mail.extra.RECIPIENTS"
internal const val LEGACY_ROLES = "com.otus.mail.extra.ROLES"
internal const val LEGACY_CAN_SEND = "com.otus.mail.extra.CAN_SEND"
