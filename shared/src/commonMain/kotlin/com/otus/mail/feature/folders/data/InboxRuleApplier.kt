package com.otus.mail.feature.folders.data

import com.otus.jmap.GetMethod
import com.otus.jmap.GetResult
import com.otus.jmap.JmapCall
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapIds
import com.otus.jmap.JmapType
import com.otus.jmap.QueryMethod
import com.otus.jmap.QueryResult
import com.otus.jmap.SetMethod
import com.otus.mail.core.mail.EmailAddress
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.MailboxType
import com.otus.mail.core.mail.NEWEST_FIRST
import com.otus.mail.core.mail.inMailbox
import com.otus.mail.core.mail.pointer
import com.otus.mail.feature.folders.domain.ApplyOutcome
import com.otus.mail.feature.folders.domain.INBOX_ROLE
import com.otus.mail.feature.folders.domain.InboxMatches
import com.otus.mail.feature.folders.domain.MailRule
import com.otus.mail.feature.folders.domain.RuleAddress
import com.otus.mail.feature.folders.domain.RuleMessage
import com.otus.mail.feature.folders.domain.matches
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
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
        val matches = matches(rule)
        return ApplyOutcome.Applied(move(matches, rule), matches.checked, matches.more)
    }

    /** The newest inbox messages [rule] matches; none when there is no inbox or the rule files into it. */
    suspend fun matches(rule: MailRule): InboxMatches {
        val mailboxes =
            account.connection.call(GetMethod(MailboxType, account.id, properties = listOf("id", "role"))).list
        val inbox = mailboxes.firstOrNull { it.role?.lowercase() == INBOX_ROLE }
        if (inbox == null || inbox.id == rule.folderId) return InboxMatches("", emptyList(), 0, more = false)
        lateinit var query: JmapCall<QueryResult>
        lateinit var get: JmapCall<GetResult<RuleEmail>>
        val response =
            account.connection.send {
                query = call(QueryMethod(RuleEmailType, account.id, inMailbox(inbox.id), NEWEST_FIRST, limit))
                get =
                    call(
                        GetMethod(
                            RuleEmailType,
                            account.id,
                            JmapIds.Result(query.resultOf("/ids")),
                            PROPERTIES,
                            BODY,
                        ),
                    )
            }
        val emails = response[get].list
        return InboxMatches(
            inbox.id,
            emails.filter { rule.matches(it.toRuleMessage()) }.map { it.id },
            emails.size,
            more = response[query].ids.size >= limit,
        )
    }

    /** Moves [matches] out of the inbox into [rule]'s folder and returns how many moved. */
    suspend fun move(
        matches: InboxMatches,
        rule: MailRule,
    ): Int {
        if (matches.ids.isEmpty()) return 0
        val patch =
            buildJsonObject {
                put("mailboxIds/${pointer(matches.inboxId)}", JsonNull)
                put("mailboxIds/${pointer(rule.folderId)}", JsonPrimitive(true))
                if (rule.markRead) put("keywords/\$seen", JsonPrimitive(true))
            }
        val batch = (account.session.maxObjectsInSet ?: MAX_BATCH).coerceIn(1, MAX_BATCH).toInt()
        return matches.ids.chunked(batch).sumOf { chunk ->
            val method = SetMethod(RuleEmailType, account.id, update = chunk.associateWith { patch })
            account.connection
                .call(method)
                .updated.size
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 500
        const val MAX_BATCH = 100L
        val PROPERTIES = listOf("id", "from", "to", "cc", "subject", "bodyStructure")
        val BODY =
            mapOf("bodyProperties" to JsonArray(listOf("type", "name", "subParts").map(::JsonPrimitive)))
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
    val bodyStructure: RulePart? = null,
)

/** One MIME part of an Email's `bodyStructure`, with its children when it is a multipart type. */
@Serializable
internal data class RulePart(
    val type: String? = null,
    val name: String? = null,
    val subParts: List<RulePart>? = null,
)

private val RuleEmailType: JmapType<RuleEmail> = JmapType("Email", JmapCapability.MAIL, RuleEmail.serializer())

/**
 * The message as the server's rules see it. Sieve's `:mime :anychild` tests every MIME part,
 * containers and text bodies included, so attachment details here come from every part too.
 */
internal fun RuleEmail.toRuleMessage(): RuleMessage {
    val parts = bodyStructure?.withSubParts().orEmpty()
    return RuleMessage(
        from = from.orEmpty().map { RuleAddress(it.name, it.email) },
        to = to.orEmpty().map { RuleAddress(it.name, it.email) },
        cc = cc.orEmpty().map { RuleAddress(it.name, it.email) },
        subject = subject,
        attachmentTypes = parts.mapNotNull { it.type },
        attachmentNames = parts.mapNotNull { it.name },
    )
}

private fun RulePart.withSubParts(): List<RulePart> = listOf(this) + subParts.orEmpty().flatMap { it.withSubParts() }
