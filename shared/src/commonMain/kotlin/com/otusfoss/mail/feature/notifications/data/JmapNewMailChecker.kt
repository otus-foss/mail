package com.otusfoss.mail.feature.notifications.data

import com.otusfoss.core.jmap.ChangesMethod
import com.otusfoss.core.jmap.GetMethod
import com.otusfoss.core.jmap.GetResult
import com.otusfoss.core.jmap.JmapCall
import com.otusfoss.core.jmap.JmapCapability
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.JmapException
import com.otusfoss.core.jmap.JmapIds
import com.otusfoss.core.jmap.JmapResponse
import com.otusfoss.core.jmap.jmapCallOr
import com.otusfoss.core.jmap.login.JmapLogin
import com.otusfoss.core.jmap.login.LoginStore
import com.otusfoss.core.jmap.login.LoginStoreException
import com.otusfoss.mail.core.mail.DELIVERED_TO_PROPERTY
import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.mail.EmailType
import com.otusfoss.mail.core.mail.MailAccount
import com.otusfoss.mail.core.mail.ORIGINAL_TO_PROPERTY
import com.otusfoss.mail.core.mail.ThreadType
import com.otusfoss.mail.core.mail.isMuted
import com.otusfoss.mail.core.mail.mailAccount
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.core.storage.DeviceStore
import com.otusfoss.mail.feature.notifications.domain.AlertedMail
import com.otusfoss.mail.feature.notifications.domain.MailAlerts
import com.otusfoss.mail.feature.notifications.domain.NewMail
import com.otusfoss.mail.feature.notifications.domain.NewMailChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

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
    private val states: DeviceStore<String, String>,
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
        val changes =
            try {
                changePages(account, since)
            } catch (error: JmapException.MethodError) {
                // The server forgot the old state; start again from now rather than report old
                // mail, and look at every alert, since any of them may have changed.
                if (error.type != CANNOT_CALCULATE_CHANGES) throw error
                null
            }
        return changes?.let {
            LoginCheck(
                account.login.id,
                it.state,
                unreadInInbox(account, it.created.takeLast(MAX_FETCHED)),
                handled(account, showing.filter { id -> id in it.touched }),
            )
        } ?: startFromNow(account, handled(account, showing))
    }

    /**
     * Every page of changes since [since], or null past [MAX_PAGES]: saving a state short of the
     * newest would alert older mail now and the newest only later, so that far behind starts again.
     */
    private suspend fun changePages(
        account: MailAccount,
        since: String,
    ): Changes? {
        var changes = Changes(since)
        repeat(MAX_PAGES) {
            val page = account.connection.call(ChangesMethod(EmailType, account.id, changes.state, MAX_CHANGES))
            changes =
                Changes(
                    page.newState,
                    changes.created + page.created,
                    changes.touched + page.updated + page.destroyed,
                )
            if (!page.hasMoreChanges) return changes
        }
        return null
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

    private class Changes(
        val state: String,
        val created: List<String> = emptyList(),
        val touched: Set<String> = emptySet(),
    )

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

        /** Pages carry only ids, so a check reads them all up to this many, to reach the newest mail. */
        const val MAX_PAGES = 100

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
