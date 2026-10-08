package com.otus.mail.feature.folders.data

import com.otus.jmap.GetMethod
import com.otus.jmap.GetResult
import com.otus.jmap.JmapCall
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapIds
import com.otus.jmap.JmapSort
import com.otus.jmap.JmapType
import com.otus.jmap.QueryMethod
import com.otus.jmap.QueryResult
import com.otus.jmap.SetMethod
import com.otus.mail.core.mail.EmailAddress
import com.otus.mail.core.mail.MailboxType
import com.otus.mail.feature.folders.domain.ApplyOutcome
import com.otus.mail.feature.folders.domain.INBOX_ROLE
import com.otus.mail.feature.folders.domain.MailRule
import com.otus.mail.feature.folders.domain.RuleAddress
import com.otus.mail.feature.folders.domain.RuleMessage
import com.otus.mail.feature.folders.domain.matches
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Moves inbox mail that a new rule matches, since the server's rules only see mail as it arrives.
 * It checks the newest [limit] inbox messages here and moves matches with `Email/set` in batches
 * within the server's `maxObjectsInSet`.
 */
internal class InboxRuleApplier(
    private val account: MailAccount,
    private val limit: Int = DEFAULT_LIMIT,
) {
    suspend fun apply(rule: MailRule): ApplyOutcome.Applied {
        val mailboxes =
            account.connection.call(GetMethod(MailboxType, account.accountId, properties = listOf("id", "role"))).list
        val inbox = mailboxes.firstOrNull { it.role?.lowercase() == INBOX_ROLE }
        if (inbox == null || inbox.id == rule.folderId) return ApplyOutcome.Applied(0, 0, more = false)
        lateinit var query: JmapCall<QueryResult>
        lateinit var get: JmapCall<GetResult<RuleEmail>>
        val response =
            account.connection.send {
                query = call(QueryMethod(RuleEmailType, account.accountId, inMailbox(inbox.id), NEWEST_FIRST, limit))
                get =
                    call(
                        GetMethod(
                            RuleEmailType,
                            account.accountId,
                            JmapIds.Result(query.resultOf("/ids")),
                            PROPERTIES,
                            BODY,
                        ),
                    )
            }
        val emails = response[get].list
        val matching = emails.filter { rule.matches(it.toRuleMessage()) }.map { it.id }
        return ApplyOutcome.Applied(
            move(matching, inbox.id, rule),
            emails.size,
            more =
                response[query].ids.size >= limit,
        )
    }

    private suspend fun move(
        ids: List<String>,
        inboxId: String,
        rule: MailRule,
    ): Int {
        val patch =
            buildJsonObject {
                put("mailboxIds/${pointer(inboxId)}", JsonNull)
                put("mailboxIds/${pointer(rule.folderId)}", JsonPrimitive(true))
                if (rule.markRead) put("keywords/\$seen", JsonPrimitive(true))
            }
        val batch = (account.session.maxObjectsInSet ?: MAX_BATCH).coerceIn(1, MAX_BATCH).toInt()
        return ids.chunked(batch).sumOf { chunk ->
            val method = SetMethod(RuleEmailType, account.accountId, update = chunk.associateWith { patch })
            account.connection
                .call(method)
                .updated.size
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 500
        const val MAX_BATCH = 100L
        val NEWEST_FIRST = listOf(JmapSort("receivedAt", isAscending = false))
        val PROPERTIES = listOf("id", "from", "to", "cc", "subject", "attachments")
        val BODY = mapOf("bodyProperties" to JsonArray(listOf(JsonPrimitive("type"), JsonPrimitive("name"))))
    }
}

/** Only the parts of an Email that rules test (RFC 8621, section 4.1). */
@Serializable
internal data class RuleEmail(
    val id: String,
    val from: List<EmailAddress>? = null,
    val to: List<EmailAddress>? = null,
    val cc: List<EmailAddress>? = null,
    val subject: String? = null,
    val attachments: List<RuleAttachment>? = null,
)

@Serializable
internal data class RuleAttachment(
    val type: String? = null,
    val name: String? = null,
)

private val RuleEmailType: JmapType<RuleEmail> = JmapType("Email", JmapCapability.MAIL, RuleEmail.serializer())

private fun RuleEmail.toRuleMessage() =
    RuleMessage(
        from = from.orEmpty().map { RuleAddress(it.name, it.email) },
        to = to.orEmpty().map { RuleAddress(it.name, it.email) },
        cc = cc.orEmpty().map { RuleAddress(it.name, it.email) },
        subject = subject,
        attachmentTypes = attachments.orEmpty().mapNotNull { it.type },
        attachmentNames = attachments.orEmpty().mapNotNull { it.name },
    )

private fun inMailbox(id: String) = JsonObject(mapOf("inMailbox" to JsonPrimitive(id)))

/** A JSON Pointer token (RFC 6901), as patch paths use (RFC 8620, section 5.3). */
private fun pointer(id: String): String = id.replace("~", "~0").replace("/", "~1")
