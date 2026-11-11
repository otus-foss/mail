package com.otusfoss.mail.feature.read.data

import com.otusfoss.core.jmap.GetMethod
import com.otusfoss.core.jmap.GetResult
import com.otusfoss.core.jmap.JmapCall
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.JmapFilter
import com.otusfoss.core.jmap.JmapIds
import com.otusfoss.core.jmap.JmapRequestBuilder
import com.otusfoss.core.jmap.QueryMethod
import com.otusfoss.core.jmap.filterCondition
import com.otusfoss.core.jmap.jmapCallOr
import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.mail.EmailType
import com.otusfoss.mail.core.mail.MailAccount
import com.otusfoss.mail.core.mail.NEWEST_FIRST
import com.otusfoss.mail.core.mail.isSeen
import com.otusfoss.mail.core.mail.mailAccount
import com.otusfoss.mail.core.mail.receivedTime
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.core.messages.data.boxes
import com.otusfoss.mail.core.messages.data.receivedAt
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.feature.read.domain.SenderMail
import com.otusfoss.mail.feature.read.domain.SenderMails
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonPrimitive

/**
 * A sender's other mail, from two lookups in one request: `Email/query` on `from` (RFC 8621, section
 * 4.4), which reaches old mail, and the account's newest [RECENT] conversations without a filter.
 * A server's search can miss mail the account lists, such as mail it has not indexed yet, so the
 * newest mail does not depend on it. The server may also match a sender loosely, such as by
 * substring or by name, so only mail from the same address stays; mail in Trash and Spam does not
 * count.
 */
internal class JmapSenderMails(
    private val connections: JmapConnections,
    private val mailboxes: Mailboxes,
) : SenderMails {
    override suspend fun newest(
        message: MailMessage,
        except: Set<String>,
    ): List<SenderMail> =
        jmapCallOr(emptyList()) {
            connections
                .mailAccount(message.location.accountId)
                ?.newest(message.sender.address, except)
                .orEmpty()
        }

    private suspend fun MailAccount.newest(
        address: String,
        except: Set<String>,
    ): List<SenderMail> {
        lateinit var found: JmapCall<GetResult<EmailObject>>
        lateinit var recent: JmapCall<GetResult<EmailObject>>
        val (folders, response) =
            mailboxes.send(this) {
                found = newest(id, filterCondition("from", JsonPrimitive(address)), SEARCHED)
                recent = newest(id, filter = null, RECENT)
            }
        val bins =
            folders
                .filter { it.isBin }
                .map { it.id }
                .toSet()
        val zone = TimeZone.currentSystemDefault()
        return (response[found].list + response[recent].list)
            .distinctBy { it.id }
            .filter { it.id !in except && it.boxes.none(bins::contains) && it.isFrom(address) }
            .sortedByDescending { it.receivedTime }
            .take(SenderMails.SHOWN)
            .map { SenderMail(it.id, it.subject.orEmpty(), it.preview, receivedAt(it.receivedTime, zone), !it.isSeen) }
    }

    /** The newest [limit] conversations of [accountId] that match [filter], or of all its mail without one. */
    private fun JmapRequestBuilder.newest(
        accountId: String,
        filter: JmapFilter?,
        limit: Int,
    ): JmapCall<GetResult<EmailObject>> {
        val query = call(QueryMethod(EmailType, accountId, filter, NEWEST_FIRST, limit, COLLAPSED))
        return call(GetMethod(EmailType, accountId, JmapIds.Result(query.resultOf("/ids")), PROPERTIES))
    }

    private fun EmailObject.isFrom(address: String): Boolean =
        from.orEmpty().any { it.email.equals(address, ignoreCase = true) }

    private companion object {
        /** Enough to leave [SenderMails.SHOWN] once the open conversation and the bins are left out. */
        const val SEARCHED = 20

        /** How far back the unfiltered lookup reads; a few addresses and dates each. */
        const val RECENT = 100
        val COLLAPSED = mapOf("collapseThreads" to JsonPrimitive(true))
        val PROPERTIES = listOf("id", "subject", "preview", "receivedAt", "mailboxIds", "keywords", "from")
    }
}
