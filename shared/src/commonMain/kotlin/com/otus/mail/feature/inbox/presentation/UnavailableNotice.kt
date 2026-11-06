package com.otus.mail.feature.inbox.presentation

import com.otus.dateformat.DateFormatter
import com.otus.mail.core.mail.MailFailure
import com.otus.mail.feature.inbox.domain.UnavailableAccount
import com.otus.mail.feature.inbox.presentation.components.syncedLabel
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Why the account did not load, and when the mail it shows last matched the server. */
internal fun UnavailableAccount.notice(
    now: LocalDateTime,
    dates: DateFormatter,
): String {
    val updated =
        syncedAt
            ?.toLocalDateTime(TimeZone.currentSystemDefault())
            ?.let { " Last updated ${dates.syncedLabel(it, now)}." }
    return reasonNotice() + updated.orEmpty()
}

private fun UnavailableAccount.reasonNotice(): String =
    when (reason) {
        MailFailure.SignInRejected -> "$name: the server rejected the sign-in. Reconnect it in Settings."
        MailFailure.NotPermitted -> "$name: the server doesn't allow this account to read mail."
        MailFailure.CredentialsUnavailable -> "$name: the saved password couldn't be read. Reconnect it in Settings."
        MailFailure.NoMail -> "$name: this account doesn't offer mail over JMAP."
        MailFailure.Unreachable -> "$name: couldn't reach the server."
        MailFailure.ServerProblem -> "$name: the server returned an error."
    }
