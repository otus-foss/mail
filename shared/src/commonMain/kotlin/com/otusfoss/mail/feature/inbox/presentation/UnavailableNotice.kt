package com.otusfoss.mail.feature.inbox.presentation

import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.core.messages.presentation.syncedLabel
import com.otusfoss.mail.core.text.accountLabel
import com.otusfoss.mail.feature.inbox.domain.UnavailableAccount
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Why these accounts, out of the [accounts] loaded, did not load, one sentence per reason, and
 * when the oldest of their mail last matched the server. Accounts are named only when some of
 * several are affected.
 */
internal fun List<UnavailableAccount>.notice(
    accounts: Int,
    now: LocalDateTime,
    dates: DateFormatter,
): String {
    val reasons =
        groupBy { it.reason }.map { (reason, group) ->
            val text = reason.text()
            accountLabel(group.map { it.name }, accounts)
                ?.let { "$it: $text" }
                ?: text.replaceFirstChar(Char::uppercase)
        }
    val updated =
        mapNotNull { it.syncedAt }
            .minOrNull()
            ?.toLocalDateTime(TimeZone.currentSystemDefault())
            ?.let { " Last updated ${dates.syncedLabel(it, now)}." }
    return reasons.joinToString(" ") + updated.orEmpty()
}

private fun MailFailure.text(): String =
    when (this) {
        MailFailure.SignInRejected -> "the server rejected the sign-in. Reconnect in Settings."
        MailFailure.NotPermitted -> "the server doesn't allow reading mail."
        MailFailure.CredentialsUnavailable -> "the saved password couldn't be read. Reconnect in Settings."
        MailFailure.NoMail -> "the server doesn't offer mail over JMAP."
        MailFailure.Unreachable -> "couldn't reach the server."
        MailFailure.ServerProblem -> "the server returned an error."
    }
