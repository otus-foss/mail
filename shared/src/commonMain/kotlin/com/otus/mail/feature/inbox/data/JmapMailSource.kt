package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnection
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.jmapDecoding
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.mail.MailboxObject
import com.otus.mail.core.mail.MailboxType
import com.otus.mail.feature.inbox.domain.InboxFailure
import com.otus.mail.feature.inbox.domain.InboxSnapshot
import com.otus.mail.feature.inbox.domain.InboxStorageException
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.domain.UnavailableAccount
import com.otus.mail.feature.inbox.domain.mailboxName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone

/**
 * Loads all or named mailboxes of each login, or chosen folders, as one row per conversation,
 * newest first. Logins load in parallel and fail alone.
 */
class JmapMailSource(
    private val connections: JmapConnections,
    private val logins: LoginStore,
    private val limit: Int = DEFAULT_LIMIT,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : MailSource {
    override suspend fun messages(scope: MailScope): InboxSnapshot {
        val saved =
            withContext(Dispatchers.IO) {
                try {
                    logins.logins()
                } catch (error: LoginStoreException) {
                    throw InboxStorageException(error)
                }
            }
        val selected = saved.filter { scope.reads(it.id) }
        val results = coroutineScope { selected.map { async { load(it, scope) } }.awaitAll() }
        return InboxSnapshot(
            messages = results.filterIsInstance<AccountLoad.Loaded>().flatMap { it.messages },
            unavailable = results.filterIsInstance<AccountLoad.Failed>().map { it.account },
        )
    }

    private suspend fun load(
        login: JmapLogin,
        scope: MailScope,
    ): AccountLoad =
        try {
            val connection = connections.connection(login)
            val accountId = connection.session().accountFor(JmapCapability.MAIL)
            if (accountId == null) {
                AccountLoad.Failed(UnavailableAccount(login.name, InboxFailure.NoMail))
            } else {
                AccountLoad.Loaded(loadMailboxes(connection, login, accountId, scope))
            }
        } catch (error: JmapException) {
            AccountLoad.Failed(UnavailableAccount(login.name, error.toInboxFailure()))
        }

    private suspend fun loadMailboxes(
        connection: JmapConnection,
        login: JmapLogin,
        accountId: String,
        scope: MailScope,
    ): List<MailMessage> {
        val mailboxes =
            connection
                .call(
                    GetMethod(MailboxType, accountId, properties = listOf("id", "name", "role")),
                ).list
        val chosen = scope.chosen(mailboxes, login.id)
        val view =
            ThreadView(
                shown = chosen.map { it.id }.toSet(),
                trashAndJunk = mailboxes.filter { it.role in HIDDEN_ROLES }.map { it.id }.toSet(),
                sent = mailboxes.filter { it.role == SENT_ROLE }.map { it.id }.toSet(),
            )
        // Each login loads its mailboxes sequentially instead of starting one request per folder.
        val loaded =
            chosen.map { mailbox ->
                val name =
                    when (scope) {
                        is MailScope.Role -> scope.name
                        is MailScope.Roles -> scope.names[mailbox.role] ?: mailboxName(mailbox.name, mailbox.role)
                        else -> mailboxName(mailbox.name, mailbox.role)
                    }
                loadMailbox(connection, login, accountId, mailbox.id, name, view)
            }
        // A thread can be in several chosen mailboxes; it is listed once.
        return loaded.flatten().mergedByThread()
    }

    private suspend fun loadMailbox(
        connection: JmapConnection,
        login: JmapLogin,
        accountId: String,
        mailboxId: String,
        name: String,
        view: ThreadView,
    ): List<MailMessage> {
        val emails = connection.threadedEmails(accountId, mailboxId, limit)
        val local = zone()
        return jmapDecoding {
            emails.rows.map { row ->
                row
                    .toMessage(login.id, login.name, name, local, login.username, mailboxId)
                    .inConversation(row, emails.threads[row.threadId].orEmpty(), mailboxId, view, local)
            }
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 50

        /** Roles whose emails a conversation counts only when the list shows them. */
        val HIDDEN_ROLES = setOf("trash", "junk")

        const val SENT_ROLE = "sent"
    }
}

/** The mailboxes of [all] of the login with [loginId] that this scope loads. */
private fun MailScope.chosen(
    all: List<MailboxObject>,
    loginId: String,
): List<MailboxObject> =
    when (this) {
        is MailScope.Role -> {
            all.filter { it.role == role }
        }

        is MailScope.Roles -> {
            all.filter { it.role in names || (ownFolders && it.role == null) }
        }

        MailScope.AllMailboxes -> {
            all
        }

        is MailScope.Mailboxes -> {
            all.filter { mailbox ->
                mailboxes.isEmpty() ||
                    mailboxes.any {
                        it.name == mailboxName(mailbox.name, mailbox.role) &&
                            (it.account == null || it.account == loginId)
                    }
            }
        }

        is MailScope.Folder -> {
            all.filter { it.id == folderId }
        }

        is MailScope.Folders -> {
            all.filter { MailScope.Folder(loginId, it.id) in folders }
        }
    }

/** Whether this scope reads mail of the login with [loginId]. */
private fun MailScope.reads(loginId: String): Boolean =
    when (this) {
        MailScope.AllMailboxes, is MailScope.Role, is MailScope.Roles -> {
            true
        }

        is MailScope.Mailboxes -> {
            (accounts.isEmpty() || loginId in accounts) &&
                (mailboxes.isEmpty() || mailboxes.any { it.account == null || it.account == loginId })
        }

        is MailScope.Folder -> {
            accountId == loginId
        }

        is MailScope.Folders -> {
            folders.any { it.accountId == loginId }
        }
    }

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
