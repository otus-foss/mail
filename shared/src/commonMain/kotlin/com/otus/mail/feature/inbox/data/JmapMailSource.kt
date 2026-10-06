package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnection
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.JmapIds
import com.otus.jmap.JmapSort
import com.otus.jmap.QueryMethod
import com.otus.jmap.jmapDecoding
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.mail.EMAIL_PROPERTIES
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.MailboxType
import com.otus.mail.feature.inbox.domain.InboxFailure
import com.otus.mail.feature.inbox.domain.InboxSnapshot
import com.otus.mail.feature.inbox.domain.InboxStorageException
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.domain.UnavailableAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Loads each login's inbox, newest first. Logins load in parallel and fail alone. */
class JmapMailSource(
    private val connections: JmapConnections,
    private val logins: LoginStore,
    private val limit: Int = DEFAULT_LIMIT,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : MailSource {
    override suspend fun inbox(): InboxSnapshot {
        val saved =
            withContext(Dispatchers.IO) {
                try {
                    logins.logins()
                } catch (error: LoginStoreException) {
                    throw InboxStorageException(error)
                }
            }
        val results = coroutineScope { saved.map { async { load(it) } }.awaitAll() }
        return InboxSnapshot(
            messages = results.filterIsInstance<AccountLoad.Loaded>().flatMap { it.messages },
            unavailable = results.filterIsInstance<AccountLoad.Failed>().map { it.account },
        )
    }

    private suspend fun load(login: JmapLogin): AccountLoad =
        try {
            val connection = connections.connection(login)
            val accountId = connection.session().accountFor(JmapCapability.MAIL)
            if (accountId == null) {
                AccountLoad.Failed(UnavailableAccount(login.name, InboxFailure.NoMail))
            } else {
                AccountLoad.Loaded(loadInbox(connection, login, accountId))
            }
        } catch (error: JmapException) {
            AccountLoad.Failed(UnavailableAccount(login.name, error.toInboxFailure()))
        }

    private suspend fun loadInbox(
        connection: JmapConnection,
        login: JmapLogin,
        accountId: String,
    ): List<MailMessage> {
        val mailboxes =
            connection
                .call(
                    GetMethod(MailboxType, accountId, properties = listOf("id", "name", "role")),
                ).list
        // Located by role, not name: servers name and translate the inbox differently.
        val inbox = mailboxes.firstOrNull { it.role == INBOX_ROLE } ?: return emptyList()
        val emails =
            connection.request {
                val query = call(QueryMethod(EmailType, accountId, inMailbox(inbox.id), NEWEST_FIRST, limit))
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
            emails.list.map { it.toMessage(login.id, login.name, INBOX_NAME, local, login.username) }
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 50
        const val INBOX_ROLE = "inbox"
        const val INBOX_NAME = "Inbox"

        const val MAX_BODY_BYTES = 20_000
        val NEWEST_FIRST = listOf(JmapSort("receivedAt", isAscending = false))

        /** Text bodies up to [MAX_BODY_BYTES] per part (RFC 8621, section 4.2). */
        val BODY_ARGUMENTS =
            mapOf(
                "bodyProperties" to JsonArray(listOf(JsonPrimitive("partId"), JsonPrimitive("name"))),
                "fetchTextBodyValues" to JsonPrimitive(true),
                "maxBodyValueBytes" to JsonPrimitive(MAX_BODY_BYTES),
            )
    }
}

private fun inMailbox(id: String) = buildJsonObject { put("inMailbox", JsonPrimitive(id)) }

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
