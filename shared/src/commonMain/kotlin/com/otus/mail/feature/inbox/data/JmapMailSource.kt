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
import com.otus.mail.core.mail.NEWEST_FIRST
import com.otus.mail.core.mail.inMailbox
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Loads all or named mailboxes of each login, or chosen folders, newest first. Logins load in
 * parallel and fail alone.
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
        val chosen =
            when (scope) {
                is MailScope.Role -> {
                    mailboxes.filter { it.role == scope.role }
                }

                MailScope.AllMailboxes -> {
                    mailboxes
                }

                is MailScope.Mailboxes -> {
                    mailboxes.filter { mailbox ->
                        scope.mailboxes.isEmpty() ||
                            scope.mailboxes.any {
                                it.name == mailboxName(mailbox.name, mailbox.role) &&
                                    (it.account == null || it.account == login.id)
                            }
                    }
                }

                is MailScope.Folder -> {
                    mailboxes.filter { it.id == scope.folderId }
                }

                is MailScope.Folders -> {
                    mailboxes.filter { MailScope.Folder(login.id, it.id) in scope.folders }
                }
            }
        // Each login loads its mailboxes sequentially instead of starting one request per folder.
        val loaded =
            chosen.map { mailbox ->
                val name = if (scope is MailScope.Role) scope.name else mailboxName(mailbox.name, mailbox.role)
                loadMailbox(connection, login, accountId, mailbox.id, name)
            }
        // A message can be in several chosen mailboxes; it is listed once, under the first.
        return loaded.flatten().distinctBy { it.id }
    }

    private suspend fun loadMailbox(
        connection: JmapConnection,
        login: JmapLogin,
        accountId: String,
        mailboxId: String,
        name: String,
    ): List<MailMessage> {
        val emails =
            connection.request {
                val query = call(QueryMethod(EmailType, accountId, inMailbox(mailboxId), NEWEST_FIRST, limit))
                call(
                    GetMethod(
                        EmailType,
                        accountId,
                        JmapIds.Result(query.resultOf("/ids")),
                        EMAIL_PROPERTIES,
                        BODY_ARGUMENTS,
                    ),
                )
            }
        val local = zone()
        return jmapDecoding {
            emails.list.map { it.toMessage(login.id, login.name, name, local, login.username, mailboxId) }
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 50

        const val MAX_BODY_BYTES = 20_000

        /** Text bodies up to [MAX_BODY_BYTES] per part (RFC 8621, section 4.2). */
        val BODY_ARGUMENTS =
            mapOf(
                "bodyProperties" to JsonArray(listOf(JsonPrimitive("partId"), JsonPrimitive("name"))),
                "fetchTextBodyValues" to JsonPrimitive(true),
                "maxBodyValueBytes" to JsonPrimitive(MAX_BODY_BYTES),
            )
    }
}

/** Whether this scope reads mail of the login with [loginId]. */
private fun MailScope.reads(loginId: String): Boolean =
    when (this) {
        MailScope.AllMailboxes, is MailScope.Role -> {
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
