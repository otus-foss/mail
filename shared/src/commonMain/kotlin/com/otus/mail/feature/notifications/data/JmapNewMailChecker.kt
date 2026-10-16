package com.otus.mail.feature.notifications.data

import com.otus.jmap.ChangesMethod
import com.otus.jmap.GetMethod
import com.otus.jmap.GetResult
import com.otus.jmap.JmapCall
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.JmapIds
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginStore
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.MailAccounts
import com.otus.mail.core.mail.MailboxObject
import com.otus.mail.core.mail.MailboxType
import com.otus.mail.feature.notifications.domain.MailStateStore
import com.otus.mail.feature.notifications.domain.NewMail
import com.otus.mail.feature.notifications.domain.NewMailChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Reads `Email/changes` since each login's saved state (RFC 8620, section 5.2) and reports created
 * mail that is unread and in an inbox. Logins are checked in parallel and fail alone.
 */
class JmapNewMailChecker(
    connections: JmapConnections,
    logins: LoginStore,
    private val states: MailStateStore,
) : NewMailChecker {
    private val accounts = MailAccounts(connections, logins)

    override suspend fun check(show: suspend (List<NewMail>) -> Unit) {
        val saved = withContext(Dispatchers.IO) { states.read() }
        val checked = coroutineScope { accounts.logins().map { async { check(it, saved[it.id]) } }.awaitAll() }
        checked
            .flatMap { it.mail }
            .sortedBy { it.receivedAt }
            .takeIf { it.isNotEmpty() }
            ?.let { show(it) }
        val next = checked.mapNotNull { login -> (login.state ?: saved[login.loginId])?.let { login.loginId to it } }
        withContext(Dispatchers.IO) { states.write(next.toMap()) }
    }

    private suspend fun check(
        login: JmapLogin,
        since: String?,
    ): LoginCheck =
        try {
            val account = accounts.open(login)
            since?.let { changesSince(account, it) } ?: startFromNow(account)
        } catch (_: JmapException) {
            LoginCheck(login.id, state = null, mail = emptyList())
        }

    private suspend fun startFromNow(account: MailAccount): LoginCheck {
        val state = account.connection.call(GetMethod(EmailType, account.id, JmapIds.Of(emptyList()))).state
        return LoginCheck(account.login.id, state, emptyList())
    }

    private suspend fun changesSince(
        account: MailAccount,
        since: String,
    ): LoginCheck {
        var state = since
        var created = emptyList<String>()
        var pages = 0
        do {
            val page =
                try {
                    account.connection.call(ChangesMethod(EmailType, account.id, state, MAX_CHANGES))
                } catch (error: JmapException.MethodError) {
                    // The server forgot the old state; start again from now rather than report old mail.
                    if (error.type == CANNOT_CALCULATE_CHANGES) return startFromNow(account) else throw error
                }
            created = created + page.created
            state = page.newState
            pages++
        } while (page.hasMoreChanges && pages < MAX_PAGES)
        return LoginCheck(account.login.id, state, unreadInInbox(account, created.takeLast(MAX_FETCHED)))
    }

    private suspend fun unreadInInbox(
        account: MailAccount,
        ids: List<String>,
    ): List<NewMail> {
        if (ids.isEmpty()) return emptyList()
        lateinit var mailboxes: JmapCall<GetResult<MailboxObject>>
        lateinit var emails: JmapCall<GetResult<EmailObject>>
        val response =
            account.connection.send {
                mailboxes = call(GetMethod(MailboxType, account.id, properties = listOf("id", "role")))
                emails = call(GetMethod(EmailType, account.id, JmapIds.Of(ids), EMAIL_PROPERTIES))
            }
        val inboxes =
            response[mailboxes]
                .list
                .filter { it.role?.lowercase() == INBOX_ROLE }
                .map { it.id }
                .toSet()
        return response[emails]
            .list
            .filter { email -> email.mailboxIds.any { it.value && it.key in inboxes } }
            .filterNot { it.keywords["\$seen"] == true || it.keywords["\$draft"] == true }
            .map { it.toNewMail(account) }
    }

    private class LoginCheck(
        val loginId: String,
        /** Null when the check failed and the saved state stays. */
        val state: String?,
        val mail: List<NewMail>,
    )

    private companion object {
        const val CANNOT_CALCULATE_CHANGES = "cannotCalculateChanges"
        const val INBOX_ROLE = "inbox"
        const val MAX_CHANGES = 100
        const val MAX_PAGES = 5

        /** After a long time offline, only the latest messages are worth a notification. */
        const val MAX_FETCHED = 50

        val EMAIL_PROPERTIES = listOf("id", "mailboxIds", "keywords", "from", "subject", "preview", "receivedAt")
    }
}

private fun EmailObject.toNewMail(account: MailAccount): NewMail {
    val from = from?.firstOrNull()
    return NewMail(
        loginId = account.login.id,
        accountName = account.login.name,
        emailId = id,
        sender = from?.name?.takeIf { it.isNotBlank() } ?: from?.email.orEmpty(),
        subject = subject.orEmpty(),
        preview = preview,
        // One malformed date must not stop every later check.
        receivedAt = Instant.parseOrNull(receivedAt) ?: Clock.System.now(),
    )
}
