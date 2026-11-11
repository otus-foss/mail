package com.otusfoss.mail.feature.read.data

import com.otusfoss.core.jmap.GetMethod
import com.otusfoss.core.jmap.GetResult
import com.otusfoss.core.jmap.JmapCall
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.JmapIds
import com.otusfoss.core.jmap.QueryMethod
import com.otusfoss.core.jmap.filterCondition
import com.otusfoss.core.jmap.jmapCallOr
import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.mail.EmailType
import com.otusfoss.mail.core.mail.MailAccount
import com.otusfoss.mail.core.mail.NEWEST_FIRST
import com.otusfoss.mail.core.mail.isSeen
import com.otusfoss.mail.core.mail.mailAccount
import com.otusfoss.mail.core.mail.received
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.core.messages.data.boxes
import com.otusfoss.mail.core.messages.data.receivedAt
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.feature.read.domain.SenderMail
import com.otusfoss.mail.feature.read.domain.SenderMails
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonPrimitive

/**
 * A sender's other mail, found with `Email/query` on `from` with threads collapsed (RFC 8621,
 * section 4.4). The server may match a sender loosely, such as by substring or by name, so only
 * mail from the same address stays; mail in Trash and Spam does not count.
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
        lateinit var emails: JmapCall<GetResult<EmailObject>>
        val (folders, response) =
            mailboxes.send(this) {
                val query =
                    call(
                        QueryMethod(
                            EmailType,
                            id,
                            filterCondition("from", JsonPrimitive(address)),
                            NEWEST_FIRST,
                            SCAN,
                            COLLAPSED,
                        ),
                    )
                emails = call(GetMethod(EmailType, id, JmapIds.Result(query.resultOf("/ids")), PROPERTIES))
            }
        val bins =
            folders
                .filter { it.isBin }
                .map { it.id }
                .toSet()
        val zone = TimeZone.currentSystemDefault()
        return response[emails]
            .list
            .filter { it.id !in except && it.boxes.none(bins::contains) && it.isFrom(address) }
            .take(SenderMails.SHOWN)
            .mapNotNull { email ->
                email.received?.let {
                    SenderMail(email.id, email.subject.orEmpty(), email.preview, receivedAt(it, zone), !email.isSeen)
                }
            }
    }

    private fun EmailObject.isFrom(address: String): Boolean =
        from.orEmpty().any { it.email.equals(address, ignoreCase = true) }

    private companion object {
        /** Enough to leave [SenderMails.SHOWN] once the open conversation and the bins are left out. */
        const val SCAN = 20
        val COLLAPSED = mapOf("collapseThreads" to JsonPrimitive(true))
        val PROPERTIES = listOf("id", "subject", "preview", "receivedAt", "mailboxIds", "keywords", "from")
    }
}
