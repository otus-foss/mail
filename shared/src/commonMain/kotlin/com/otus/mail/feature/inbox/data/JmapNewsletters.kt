package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.GetResult
import com.otus.jmap.JmapCall
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapIds
import com.otus.jmap.QueryMethod
import com.otus.jmap.allOf
import com.otus.jmap.filterCondition
import com.otus.jmap.jmapCallOr
import com.otus.jmap.login.LoginStore
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.cache.withAccount
import com.otus.mail.core.mail.EmailFolders
import com.otus.mail.core.mail.EmailFoldersType
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.LIST_ID_PROPERTY
import com.otus.mail.core.mail.LIST_UNSUBSCRIBE_PROPERTY
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.NEWEST_FIRST
import com.otus.mail.core.mail.mailAccount
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.mailbox.Mailboxes
import com.otus.mail.feature.inbox.domain.DraftRepository
import com.otus.mail.feature.inbox.domain.DraftSender
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailingList
import com.otus.mail.feature.inbox.domain.Newsletters
import com.otus.mail.feature.inbox.domain.Unsubscribe
import com.otus.mail.feature.inbox.domain.UnsubscribeResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Mailing lists from their headers (RFC 2369, RFC 2919). A list's older mails are the newest
 * [SCAN] with the same `List-Id`, or from the same sender when it has none, found with
 * `Email/query` (RFC 8621, section 4.4); mail in Trash and Spam does not count.
 */
class JmapNewsletters(
    private val connections: JmapConnections,
    drafts: DraftRepository,
    sender: DraftSender,
    private val cache: MailCache = InMemoryMailCache(),
) : Newsletters {
    private val unsubscriber = ListUnsubscriber(drafts, sender)
    private val mailboxes = Mailboxes(cache)

    override suspend fun list(message: MailMessage): MailingList? =
        jmapCallOr(null) {
            connections.mailAccount(message.location.accountId)?.list(message)
        }

    override suspend fun unsubscribe(
        message: MailMessage,
        how: Unsubscribe,
    ): UnsubscribeResult = unsubscriber.unsubscribe(message, how)

    override suspend fun archive(
        accountId: String,
        emailIds: List<String>,
    ): Boolean {
        return cache.withAccount(accountId) { accountCache ->
            if (emailIds.isEmpty()) return@withAccount true
            val patches =
                jmapCallOr(null) { connections.mailAccount(accountId)?.archivePatches(emailIds) }
                    ?: return@withAccount false
            patches.isEmpty() ||
                updateAccountEmails(connections, accountId, patches, accountCache).containsAll(patches.keys)
        }
    }

    private suspend fun MailAccount.list(message: MailMessage): MailingList? {
        lateinit var emails: JmapCall<GetResult<EmailObject>>
        val (folders, first) =
            mailboxes.send(this) {
                emails = call(GetMethod(EmailType, id, JmapIds.Of(listOf(message.emailId)), LIST_PROPERTIES))
            }
        val email =
            first[emails].list.firstOrNull()?.takeIf { it.fromList }
                ?: return null
        val skipped =
            folders
                .filter { it.isBin }
                .map { it.id }
                .toSet()
        val older =
            olderMail(email)
                .filter { it.id != email.id && it.boxes.none { box -> box in skipped } }
        return MailingList(
            name = listName(email.listId) ?: message.sender.name,
            perWeek = perWeek(listOf(email.receivedAt) + older.map { it.receivedAt }),
            olderIds = older.map { it.id },
        )
    }

    /**
     * The list's mail received before [email], newest first. The server may match a header or a
     * sender loosely, such as by substring or by word, or the sender's name too (RFC 8621, section
     * 4.4.1), so only mail with the same list id, or from the same address, stays.
     */
    private suspend fun MailAccount.olderMail(email: EmailObject): List<EmailObject> {
        val listId = listIdentifier(email.listId)
        val address = email.from?.firstOrNull()?.email
        val same =
            when {
                listId != null -> filterCondition("header", JsonArray(listOf("List-Id", listId).map(::JsonPrimitive)))
                !address.isNullOrBlank() -> filterCondition("from", JsonPrimitive(address))
                else -> return emptyList()
            }
        val filter = allOf(listOf(same, filterCondition("before", JsonPrimitive(email.receivedAt))))
        return connection
            .request {
                val query = call(QueryMethod(EmailType, id, filter, NEWEST_FIRST, SCAN))
                call(GetMethod(EmailType, id, JmapIds.Result(query.resultOf("/ids")), OLDER_PROPERTIES))
            }.list
            .filter { it.sameList(listId, address) }
    }

    /** Each email out of every mailbox it is in and into Archive; null without an Archive folder. */
    private suspend fun MailAccount.archivePatches(emailIds: List<String>): Map<String, JsonObject>? {
        lateinit var emails: JmapCall<GetResult<EmailFolders>>
        val (folders, response) =
            mailboxes.send(this) {
                emails = call(GetMethod(EmailFoldersType, id, JmapIds.Of(emailIds), listOf("id", "mailboxIds")))
            }
        val target = folders.firstOrNull { it.role == MailboxRole.Archive }?.id ?: return null
        return response[emails].list.associate { email -> email.id to movePatch(email.mailboxIds.keys, target) }
    }

    private companion object {
        const val SCAN = 100
        val LIST_PROPERTIES =
            listOf("id", "receivedAt", "from", LIST_ID_PROPERTY, LIST_UNSUBSCRIBE_PROPERTY)
        val OLDER_PROPERTIES = listOf("id", "receivedAt", "mailboxIds", "from", LIST_ID_PROPERTY)
    }
}

/** Whether this email has the list id [listId], or, without one, comes from [address]; neither counts case. */
private fun EmailObject.sameList(
    listId: String?,
    address: String?,
): Boolean =
    if (listId != null) {
        listIdentifier(this.listId).equals(listId, ignoreCase = true)
    } else {
        from?.firstOrNull()?.email.equals(address, ignoreCase = true)
    }

/**
 * How many mails a week arrive at the times [received]: the gaps between them over the weeks they
 * span. Null below [MIN_MAILS] mails or when they all came at once.
 */
internal fun perWeek(received: List<String>): Double? {
    val times = received.mapNotNull { Instant.parseOrNull(it) }
    if (times.size < MIN_MAILS) return null
    val weeks = (times.max() - times.min()) / 7.days
    return if (weeks > 0) (times.size - 1) / weeks else null
}

private const val MIN_MAILS = 3
