package com.otus.mail

import android.content.Intent
import androidx.work.Data
import com.otus.mail.feature.notifications.domain.NewMail
import kotlin.time.Instant

// What a button carries about its alert, so a change that fails can post the same alert again.
private const val EXTRA_ACCOUNT = "com.otus.mail.extra.ACCOUNT"
private const val EXTRA_SENDER = "com.otus.mail.extra.SENDER"
private const val EXTRA_SUBJECT = "com.otus.mail.extra.SUBJECT"
private const val EXTRA_PREVIEW = "com.otus.mail.extra.PREVIEW"
private const val EXTRA_RECEIVED = "com.otus.mail.extra.RECEIVED"
private const val EXTRA_RECIPIENTS = "com.otus.mail.extra.RECIPIENTS"
private const val EXTRA_ROLES = "com.otus.mail.extra.ROLES"
private const val EXTRA_CAN_SEND = "com.otus.mail.extra.CAN_SEND"

/** This intent with [mail]'s alert in its extras. */
internal fun Intent.putMail(mail: NewMail): Intent =
    putExtra(EXTRA_LOGIN_ID, mail.loginId)
        .putExtra(EXTRA_EMAIL_ID, mail.emailId)
        .putExtra(EXTRA_ACCOUNT, mail.accountName)
        .putExtra(EXTRA_SENDER, mail.sender)
        .putExtra(EXTRA_SUBJECT, mail.subject)
        .putExtra(EXTRA_PREVIEW, mail.preview)
        .putExtra(EXTRA_RECEIVED, mail.receivedAt.toEpochMilliseconds())
        .putExtra(EXTRA_RECIPIENTS, mail.recipients.toTypedArray())
        .putExtra(EXTRA_ROLES, mail.mailboxRoles.toTypedArray())
        .putExtra(EXTRA_CAN_SEND, mail.canSend)

/** The alert [putMail] put in this intent; null without its ids. */
internal fun Intent.mail(): NewMail? {
    val loginId = getStringExtra(EXTRA_LOGIN_ID)
    val emailId = getStringExtra(EXTRA_EMAIL_ID)
    if (loginId == null || emailId == null) return null
    return NewMail(
        loginId = loginId,
        accountName = getStringExtra(EXTRA_ACCOUNT).orEmpty(),
        emailId = emailId,
        sender = getStringExtra(EXTRA_SENDER).orEmpty(),
        subject = getStringExtra(EXTRA_SUBJECT).orEmpty(),
        preview = getStringExtra(EXTRA_PREVIEW).orEmpty(),
        receivedAt = Instant.fromEpochMilliseconds(getLongExtra(EXTRA_RECEIVED, 0)),
        recipients = getStringArrayExtra(EXTRA_RECIPIENTS).orEmpty().toList(),
        mailboxRoles = getStringArrayExtra(EXTRA_ROLES).orEmpty().toSet(),
        canSend = getBooleanExtra(EXTRA_CAN_SEND, false),
    )
}

/** [mail]'s alert as work input, next to what [Data.Builder] already holds. */
internal fun Data.Builder.putMail(mail: NewMail): Data.Builder =
    putString(EXTRA_LOGIN_ID, mail.loginId)
        .putString(EXTRA_EMAIL_ID, mail.emailId)
        .putString(EXTRA_ACCOUNT, mail.accountName)
        .putString(EXTRA_SENDER, mail.sender)
        .putString(EXTRA_SUBJECT, mail.subject)
        .putString(EXTRA_PREVIEW, mail.preview)
        .putLong(EXTRA_RECEIVED, mail.receivedAt.toEpochMilliseconds())
        .putStringArray(EXTRA_RECIPIENTS, mail.recipients.toTypedArray())
        .putStringArray(EXTRA_ROLES, mail.mailboxRoles.toTypedArray())
        .putBoolean(EXTRA_CAN_SEND, mail.canSend)

/** The alert [Data.Builder.putMail] put in this input; null without its ids. */
internal fun Data.mail(): NewMail? {
    val loginId = getString(EXTRA_LOGIN_ID)
    val emailId = getString(EXTRA_EMAIL_ID)
    if (loginId == null || emailId == null) return null
    return NewMail(
        loginId = loginId,
        accountName = getString(EXTRA_ACCOUNT).orEmpty(),
        emailId = emailId,
        sender = getString(EXTRA_SENDER).orEmpty(),
        subject = getString(EXTRA_SUBJECT).orEmpty(),
        preview = getString(EXTRA_PREVIEW).orEmpty(),
        receivedAt = Instant.fromEpochMilliseconds(getLong(EXTRA_RECEIVED, 0)),
        recipients = getStringArray(EXTRA_RECIPIENTS).orEmpty().toList(),
        mailboxRoles = getStringArray(EXTRA_ROLES).orEmpty().toSet(),
        canSend = getBoolean(EXTRA_CAN_SEND, false),
    )
}
