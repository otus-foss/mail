package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.JmapIds
import com.otus.jmap.JmapSort
import com.otus.jmap.JmapType
import com.otus.jmap.QueryMethod
import com.otus.jmap.jmapDecoding
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.SenderHistory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Reads the folders of the sender's newest [limit] messages with one `Email/query` filtered by
 * `from` and one `Email/get` of their `mailboxIds` (RFC 8621, sections 4.4 and 4.2). The server
 * matches `from` as text in the From header, so the address alone finds that sender's mail.
 */
class JmapSenderHistory(
    private val connections: JmapConnections,
    private val logins: LoginStore,
    private val limit: Int = DEFAULT_LIMIT,
) : SenderHistory {
    override suspend fun recentFolders(message: MailMessage): List<Set<String>> {
        val address = message.sender.address
        if (address.isBlank() || message.emailId.isEmpty()) return emptyList()
        return try {
            mailAccount(connections, logins, message.location.accountId)?.let { query(it, address) }.orEmpty()
        } catch (expected: JmapException) {
            emptyList()
        } catch (expected: LoginStoreException) {
            emptyList()
        }
    }

    private suspend fun query(
        account: MailAccount,
        address: String,
    ): List<Set<String>> {
        val emails =
            account.connection.request {
                val filter = buildJsonObject { put("from", JsonPrimitive(address)) }
                val query = call(QueryMethod(FolderType, account.id, filter, NEWEST_FIRST, limit))
                call(GetMethod(FolderType, account.id, JmapIds.Result(query.resultOf("/ids")), PROPERTIES))
            }
        return jmapDecoding { emails.list.map { email -> email.mailboxIds.filterValues { it }.keys } }
    }

    private companion object {
        const val DEFAULT_LIMIT = 50
        val NEWEST_FIRST = listOf(JmapSort("receivedAt", isAscending = false))
        val PROPERTIES = listOf("id", "mailboxIds")
    }
}

/** Only the folders of an Email, which is all the history needs. */
@Serializable
private data class EmailFolders(
    val id: String,
    val mailboxIds: Map<String, Boolean> = emptyMap(),
)

private val FolderType: JmapType<EmailFolders> = JmapType("Email", JmapCapability.MAIL, EmailFolders.serializer())
