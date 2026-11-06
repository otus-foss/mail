package com.otus.mail.feature.notifications.data

import com.otus.jmap.ChangesMethod
import com.otus.jmap.GetMethod
import com.otus.jmap.GetResult
import com.otus.jmap.JmapCall
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.JmapIds
import com.otus.jmap.JmapResponse
import com.otus.jmap.jmapCallOr
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.mail.DELIVERED_TO_PROPERTY
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.ORIGINAL_TO_PROPERTY
import com.otus.mail.core.mail.ThreadType
import com.otus.mail.core.mail.isDraft
import com.otus.mail.core.mail.isMuted
import com.otus.mail.core.mail.isSeen
import com.otus.mail.core.mail.mailAccount
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.mailbox.Mailboxes
import com.otus.mail.feature.notifications.domain.AlertedMail
import com.otus.mail.feature.notifications.domain.MailAlerts
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
 * mail that is unread, in an inbox, and not in a muted conversation. Alerted mail that changed
 * since is fetched again, and its alert cleared once it is read, out of the inbox, or deleted.
 * Logins are checked in parallel and fail alone. When the saved logins cannot be read, the check does nothing and the
 * next one tries again.
 */
class JmapNewMailChecker(
    private val connections: JmapConnections,
    private val mailboxes: Mailboxes,
    private val states: MailStateStore,
) : NewMailChecker {
    override suspend fun check(alerts: MailAlerts) {
        val logins =
            try {
                connections.savedLogins()
            } catch (_: LoginStoreException) {
                return
            }
        val saved = withContext(Dispatchers.IO) { states.read() }
        val showing = alerts.showing().groupBy({ it.loginId }, { it.emailId })
        val results =
            coroutineScope { logins.map { async { check(it, saved[it.id], showing[it.id].orEmpty()) } }.awaitAll() }
        // An account removed while its check ran gets no alert, and its state is not written back.
        val remaining =
            try {
                connections.savedLogins().map { it.id }.toSet()
            } catch (_: LoginStoreException) {
                return
            }
        val checked = results.filter { it.loginId in remaining }
        // Clearing first: Android may not list an alert it was just asked to show, so clearing after
        // could take an account for empty and remove its summary along with the new alert.
        checked
            .flatMap { it.handled }
            .takeIf { it.isNotEmpty() }
            ?.let { alerts.clear(it) }
        checked
            .flatMap { it.mail }
            .sortedBy { it.receivedAt }
            .takeIf { it.isNotEmpty() }
            ?.let { alerts.show(it) }
        val next = checked.mapNotNull { login -> (login.state ?: saved[login.loginId])?.let { login.loginId to it } }
        withContext(Dispatchers.IO) { states.write(next.toMap()) }
    }

    private suspend fun check(
        login: JmapLogin,
        since: String?,
        showing: List<String>,
    ): LoginCheck =
        jmapCallOr(LoginCheck(login.id, state = null, mail = emptyList())) {
            val account = connections.mailAccount(login)
            since?.let { changesSince(account, it, showing) } ?: startFromNow(account)
        }

    private suspend fun startFromNow(
        account: MailAccount,
        handled: List<AlertedMail> = emptyList(),
    ): LoginCheck {
        val state = account.connection.call(GetMethod(EmailType, account.id, JmapIds.Of(emptyList()))).state
        return LoginCheck(account.login.id, state, emptyList(), handled)
    }

    private suspend fun changesSince(
        account: MailAccount,
        since: String,
        showing: List<String>,
    ): LoginCheck {
        var state = since
        var created = emptyList<String>()
        var touched = emptySet<String>()
        var pages = 0
        do {
            val page =
                try {
                    account.connection.call(ChangesMethod(EmailType, account.id, state, MAX_CHANGES))
                } catch (error: JmapException.MethodError) {
                    // The server forgot the old state; start again from now rather than report old
                    // mail, and look at every alert, since any of them may have changed.
                    if (error.type != CANNOT_CALCULATE_CHANGES) throw error
                    return startFromNow(account, handled(account, showing))
                }
            created = created + page.created
            touched = touched + page.updated + page.destroyed
            state = page.newState
            pages++
        } while (page.hasMoreChanges && pages < MAX_PAGES)
        return LoginCheck(
            account.login.id,
            state,
            unreadInInbox(account, created.takeLast(MAX_FETCHED)),
            handled(account, showing.filter { it in touched }),
        )
    }

    /** The alerted [ids] whose mail is no longer unread in an inbox, or is gone. */
    private suspend fun handled(
        account: MailAccount,
        ids: List<String>,
    ): List<AlertedMail> {
        if (ids.isEmpty()) return emptyList()
        lateinit var emails: JmapCall<GetResult<EmailObject>>
        val (folders, response) =
            mailboxes.send(account) {
                emails =
                    call(GetMethod(EmailType, account.id, JmapIds.Of(ids), ALERTED_PROPERTIES))
            }
        val inboxes = inboxIds(folders)
        val waiting =
            response[emails]
                .list
                .filter { it.waitsIn(inboxes) }
                .map { it.id }
                .toSet()
        return ids
            .filterNot { it in waiting }
            .map { AlertedMail(account.login.id, it) }
    }

    private suspend fun unreadInInbox(
        account: MailAccount,
        ids: List<String>,
    ): List<NewMail> {
        if (ids.isEmpty()) return emptyList()
        lateinit var emails: JmapCall<GetResult<EmailObject>>
        lateinit var members: JmapCall<GetResult<EmailObject>>
        val (folders, response) =
            mailboxes.send(account) {
                emails = call(GetMethod(EmailType, account.id, JmapIds.Of(ids), EMAIL_PROPERTIES))
                val threads =
                    call(GetMethod(ThreadType, account.id, JmapIds.Result(emails.resultOf("/list/*/threadId"))))
                members =
                    call(
                        GetMethod(
                            EmailType,
                            account.id,
                            JmapIds.Result(threads.resultOf("/list/*/emailIds")),
                            MEMBER_PROPERTIES,
                        ),
                    )
            }
        val muted = mutedThreads(response, members) ?: mutedThreads(response, emails).orEmpty()
        val inboxes = inboxIds(folders)
        val roles = folders.mapNotNull { it.role }.toSet()
        val canSend =
            JmapCapability.SUBMISSION in
                account.session.accounts[account.id]
                    ?.capabilities
                    .orEmpty()
        return response[emails]
            .list
            .filter { it.waitsIn(inboxes) }
            .filterNot { it.threadId in muted }
            .map { it.toNewMail(account).copy(mailboxRoles = roles, canSend = canSend) }
    }

    /**
     * The threads with an email of [found] the user muted; null when the server refused the call.
     * A thread too long for one `Email/get` gets its members refused, so the new emails' own
     * keywords answer instead, and a refused thread mutes none, since a missed notification is
     * worse than an unwanted one.
     */
    private fun mutedThreads(
        response: JmapResponse,
        found: JmapCall<GetResult<EmailObject>>,
    ): Set<String>? =
        try {
            response[found]
                .list
                .filter { it.isMuted }
                .map { it.threadId }
                .toSet()
        } catch (expected: JmapException) {
            null
        }

    private class LoginCheck(
        val loginId: String,
        /** Null when the check failed and the saved state stays. */
        val state: String?,
        val mail: List<NewMail>,
        val handled: List<AlertedMail> = emptyList(),
    )

    private companion object {
        const val CANNOT_CALCULATE_CHANGES = "cannotCalculateChanges"
        const val MAX_CHANGES = 100
        const val MAX_PAGES = 5

        /** After a long time offline, only the latest messages are worth a notification. */
        const val MAX_FETCHED = 50

        val EMAIL_PROPERTIES =
            listOf(
                "id",
                "threadId",
                "mailboxIds",
                "keywords",
                "from",
                "to",
                "cc",
                "subject",
                "preview",
                "receivedAt",
                DELIVERED_TO_PROPERTY,
                ORIGINAL_TO_PROPERTY,
            )
        val MEMBER_PROPERTIES = listOf("id", "threadId", "keywords", "receivedAt")
        val ALERTED_PROPERTIES = listOf("id", "mailboxIds", "keywords", "receivedAt")
    }
}

private fun inboxIds(folders: List<MailFolder>): Set<String> =
    folders
        .filter { it.role == MailboxRole.Inbox }
        .map { it.id }
        .toSet()

/** Unread, not a draft, and in one of [inboxes]: mail worth an alert. */
private fun EmailObject.waitsIn(inboxes: Set<String>): Boolean =
    mailboxIds.any { it.value && it.key in inboxes } &&
        !isSeen &&
        !isDraft

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
        recipients = (to.orEmpty() + cc.orEmpty()).map { it.email } + deliveredAddresses,
    )
}
