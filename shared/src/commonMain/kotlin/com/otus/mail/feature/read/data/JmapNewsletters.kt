package com.otus.mail.feature.read.data

import com.otus.jmap.GetMethod
import com.otus.jmap.GetResult
import com.otus.jmap.JmapCall
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapIds
import com.otus.jmap.QueryMethod
import com.otus.jmap.allOf
import com.otus.jmap.filterCondition
import com.otus.jmap.jmapCallOr
import com.otus.mail.core.mail.EmailFolders
import com.otus.mail.core.mail.EmailFoldersType
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.LIST_ID_PROPERTY
import com.otus.mail.core.mail.LIST_UNSUBSCRIBE_PROPERTY
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.NEWEST_FIRST
import com.otus.mail.core.mail.mailAccount
import com.otus.mail.core.mail.received
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.mailbox.Mailboxes
import com.otus.mail.core.messages.data.boxes
import com.otus.mail.core.messages.data.receivedAt
import com.otus.mail.core.messages.domain.DraftRepository
import com.otus.mail.core.messages.domain.DraftSender
import com.otus.mail.core.messages.domain.EmailWriter
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailingList
import com.otus.mail.core.messages.domain.Unsubscribe
import com.otus.mail.feature.read.domain.Newsletters
import com.otus.mail.feature.read.domain.UnsubscribeResult
import kotlinx.serialization.json.JsonArray
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
    private val writer: EmailWriter,
    private val mailboxes: Mailboxes,
) : Newsletters {
    private val unsubscriber = ListUnsubscriber(drafts, sender)

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
        if (emailIds.isEmpty()) return true
        val archived =
            jmapCallOr(null) { connections.mailAccount(accountId)?.archived(emailIds) } ?: return false
        return archived.isEmpty() || writer.setMailboxes(accountId, archived).containsAll(archived.keys)
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
            perWeek = perWeek((listOf(email) + older).mapNotNull { it.received }),
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

    /**
     * The emails of [emailIds] that still exist, each in Archive alone, out of every mailbox it is
     * in; null without an Archive folder on the server now.
     */
    private suspend fun MailAccount.archived(emailIds: List<String>): Map<String, Set<String>>? {
        lateinit var emails: JmapCall<GetResult<EmailFolders>>
        val (folders, response) =
            mailboxes.sendFresh(this) {
                emails = call(GetMethod(EmailFoldersType, id, JmapIds.Of(emailIds), listOf("id")))
            }
        val target = folders.firstOrNull { it.role == MailboxRole.Archive }?.id ?: return null
        return response[emails].list.associate { email -> email.id to setOf(target) }
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
 * How many mails a week arrive at the [times]: the gaps between them over the weeks they
 * span. Null below [MIN_MAILS] mails or when they all came at once.
 */
internal fun perWeek(times: List<Instant>): Double? {
    if (times.size < MIN_MAILS) return null
    val weeks = (times.max() - times.min()) / 7.days
    return if (weeks > 0) (times.size - 1) / weeks else null
}

private const val MIN_MAILS = 3
