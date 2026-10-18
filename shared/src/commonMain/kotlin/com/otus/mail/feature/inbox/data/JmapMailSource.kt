package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnection
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.JmapIds
import com.otus.jmap.QueryMethod
import com.otus.jmap.jmapDecoding
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.mail.EMAIL_PROPERTIES
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.MailboxType
import com.otus.mail.feature.inbox.domain.FilterSort
import com.otus.mail.feature.inbox.domain.InboxFailure
import com.otus.mail.feature.inbox.domain.InboxSnapshot
import com.otus.mail.feature.inbox.domain.InboxStorageException
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.domain.ShowFacet
import com.otus.mail.feature.inbox.domain.UnavailableAccount
import com.otus.mail.feature.inbox.domain.fromAny
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

/**
 * Loads all or named mailboxes of each login, or chosen folders, newest first. Logins load in
 * parallel and fail alone.
 */
class JmapMailSource(
    private val connections: JmapConnections,
    private val logins: LoginStore,
    private val limit: Int = DEFAULT_LIMIT,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val clock: Clock = Clock.System,
) : MailSource {
    override suspend fun messages(scope: MailScope): InboxSnapshot = messages(scope, MailQuery())

    override suspend fun messages(
        scope: MailScope,
        query: MailQuery,
    ): InboxSnapshot {
        val saved = withContext(Dispatchers.IO) { savedLogins() }
        val now = clock.now()
        val selected = saved.filter { scope.reads(it.id) }
        val results =
            coroutineScope {
                selected
                    .map { login ->
                        async {
                            val conditions = emailConditions(query, login.id, now, zone())
                            if (conditions ==
                                null
                            ) {
                                AccountLoad.Loaded(emptyList())
                            } else {
                                load(login, scope, query, conditions)
                            }
                        }
                    }.awaitAll()
            }
        val own = query.ownAddresses
        return InboxSnapshot(
            messages =
                results
                    .filterIsInstance<AccountLoad.Loaded>()
                    .flatMap { it.messages }
                    .filter { !query.filters.has(ShowFacet.WaitingForReply) || it.fromAny(own) }
                    .filter { !query.filters.has(ShowFacet.CalendarInvites) || it.attributes.invite }
                    .filter { !query.filters.has(ShowFacet.Conversations) || it.conversation?.exchanged == true },
            unavailable = results.filterIsInstance<AccountLoad.Failed>().map { it.account },
        )
    }

    /** Sums each login's total; null when no login could be counted. */
    override suspend fun count(
        scope: MailScope,
        query: MailQuery,
    ): Long? {
        // No query finds invites or two-sided threads, so only the loaded mail can be counted.
        if (query.filters.has(ShowFacet.CalendarInvites) || query.filters.has(ShowFacet.Conversations)) return null
        val saved = withContext(Dispatchers.IO) { savedLogins() }
        val now = clock.now()
        val totals =
            coroutineScope {
                saved
                    .filter { scope.reads(it.id) }
                    .mapNotNull { login -> emailConditions(query, login.id, now, zone())?.let { login to it } }
                    .map { (login, conditions) -> async { countLogin(login, scope, query, conditions) } }
                    .awaitAll()
            }
        return totals.filterNotNull().takeIf { it.isNotEmpty() || totals.isEmpty() }?.sum()
    }

    private suspend fun countLogin(
        login: JmapLogin,
        scope: MailScope,
        query: MailQuery,
        conditions: List<JsonObject>,
    ): Long? =
        try {
            val connection = connections.connection(login)
            connection.session().accountFor(JmapCapability.MAIL)?.let { accountId ->
                val ids = scope.chosen(mailboxesOf(connection, accountId), login.id, query).map { it.id }
                if (ids.isEmpty()) {
                    0L
                } else {
                    connection
                        .call(QueryMethod(EmailType, accountId, mailFilter(ids, conditions), limit = 0, extra = COUNT))
                        .total
                }
            }
        } catch (expected: JmapException) {
            // A login that cannot be counted leaves the others' sum.
            null
        }

    private fun savedLogins(): List<JmapLogin> =
        try {
            logins.logins()
        } catch (error: LoginStoreException) {
            throw InboxStorageException(error)
        }

    private suspend fun mailboxesOf(
        connection: JmapConnection,
        accountId: String,
    ) = connection.call(GetMethod(MailboxType, accountId, properties = listOf("id", "name", "role"))).list

    private suspend fun load(
        login: JmapLogin,
        scope: MailScope,
        query: MailQuery,
        conditions: List<JsonObject>,
    ): AccountLoad =
        try {
            val connection = connections.connection(login)
            val accountId = connection.session().accountFor(JmapCapability.MAIL)
            if (accountId == null) {
                AccountLoad.Failed(UnavailableAccount(login.name, InboxFailure.NoMail))
            } else {
                AccountLoad.Loaded(loadMailboxes(connection, login, accountId, scope, query, conditions))
            }
        } catch (error: JmapException) {
            AccountLoad.Failed(UnavailableAccount(login.name, error.toInboxFailure()))
        }

    private suspend fun loadMailboxes(
        connection: JmapConnection,
        login: JmapLogin,
        accountId: String,
        scope: MailScope,
        query: MailQuery,
        conditions: List<JsonObject>,
    ): List<MailMessage> {
        val mailboxes = mailboxesOf(connection, accountId)
        val chosen = scope.chosen(mailboxes, login.id, query)
        val view =
            ThreadView(
                shown = chosen.map { it.id }.toSet(),
                trashAndJunk = mailboxes.filter { it.role in HIDDEN_ROLES }.map { it.id }.toSet(),
                sent = mailboxes.filter { it.role == SENT_ROLE }.map { it.id }.toSet(),
            )
        // Each login loads its mailboxes sequentially instead of starting one request per folder.
        val loaded =
            chosen.map { mailbox ->
                val filter = mailFilter(listOf(mailbox.id), conditions)
                val listed = Listed(mailbox.id, scope.nameOf(mailbox), filter, query.filters.sort)
                loadMailbox(connection, login, accountId, listed, view)
            }
        // A thread can be in several chosen mailboxes; it is listed once, with all of their emails.
        return loaded.flatten().mergedByThread()
    }

    private suspend fun loadMailbox(
        connection: JmapConnection,
        login: JmapLogin,
        accountId: String,
        listed: Listed,
        view: ThreadView,
    ): List<MailMessage> {
        val emails = connection.threadedEmails(accountId, listed.filter, receivedOrder(listed.sort), limit)
        val local = zone()
        return jmapDecoding {
            emails.rows.map { row ->
                row
                    .toMessage(login.id, login.name, listed.name, local, login.username, listed.mailboxId)
                    .inConversation(row, emails.threads[row.threadId].orEmpty(), listed.mailboxId, view, local)
            }
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 50

        /** Only the number of matching conversations (RFC 8620, section 5.5; RFC 8621, section 4.4.3). */
        val COUNT = mapOf("calculateTotal" to JsonPrimitive(true), "collapseThreads" to JsonPrimitive(true))

        /** Roles whose emails a conversation counts only when the list shows them. */
        val HIDDEN_ROLES = setOf("trash", "junk")

        const val SENT_ROLE = "sent"
    }
}

/** One mailbox a list loads: its [mailboxId], the [name] its mail is filed under, and the query. */
private class Listed(
    val mailboxId: String,
    val name: String,
    val filter: JsonObject,
    val sort: FilterSort,
)

private sealed interface AccountLoad {
    class Loaded(
        val messages: List<MailMessage>,
    ) : AccountLoad

    class Failed(
        val account: UnavailableAccount,
    ) : AccountLoad
}

private fun JmapException.toInboxFailure(): InboxFailure =
    when (this) {
        is JmapException.Unauthorized -> InboxFailure.SignInRejected
        is JmapException.Forbidden -> InboxFailure.NotPermitted
        is JmapException.CredentialsUnavailable -> InboxFailure.CredentialsUnavailable
        is JmapException.Unsupported -> InboxFailure.NoMail
        is JmapException.Unreachable -> InboxFailure.Unreachable
        else -> InboxFailure.ServerProblem
    }
