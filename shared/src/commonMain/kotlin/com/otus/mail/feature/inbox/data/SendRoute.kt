package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapException
import com.otus.jmap.QueryMethod
import com.otus.jmap.SetError
import com.otus.mail.core.mail.IdentityObject
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.MailboxType
import com.otus.mail.feature.inbox.domain.AnsweredMessage
import com.otus.mail.feature.inbox.domain.SendException
import com.otus.mail.feature.inbox.domain.SendFailure
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The identity a message is sent as, and the account's Drafts and Sent folders when it has them. */
internal data class SendRoute(
    val identityId: String,
    val drafts: String?,
    val sent: String?,
) {
    /**
     * The patch that files the sent copy, stored in [folder]: it leaves Drafts for Sent and loses
     * `$draft`. Without a Sent folder the copy stays where it is.
     */
    fun sentPatch(folder: String): JsonObject =
        buildJsonObject {
            if (sent != null && sent != folder) {
                put("mailboxIds/$folder", JsonNull)
                put("mailboxIds/$sent", true)
            }
            put("keywords/$DRAFT_KEYWORD", JsonNull)
        }
}

/** The identity for [address]: one with that exact address, else a wildcard such as `*@example.org`. */
internal fun List<IdentityObject>.sendingAs(address: String): IdentityObject? {
    val wanted = address.lowercase()
    val domain = wanted.substringAfterLast('@', missingDelimiterValue = "")
    return firstOrNull { it.email.lowercase() == wanted }
        ?: firstOrNull { domain.isNotEmpty() && it.email.lowercase() == "*@$domain" }
}

/** The mailbox with [role], such as `drafts` or `sent` (RFC 8621, section 2). */
internal fun MailAccount.roleQuery(role: String): QueryMethod =
    QueryMethod(MailboxType, id, buildJsonObject { put("role", role) })

internal val AnsweredMessage.keyword: String
    get() = if (forwarded) FORWARDED_KEYWORD else ANSWERED_KEYWORD

internal fun failSend(failure: SendFailure): Nothing = throw SendException(failure)

/** Error types from RFC 8621, section 7.5, and the standard ones from RFC 8620, section 5.3. */
internal fun SetError.toSendFailure(): SendFailure = sendFailure(type)

internal fun JmapException.toSendFailure(): SendFailure =
    when (this) {
        is JmapException.Forbidden -> SendFailure.NotPermitted
        is JmapException.MethodError -> sendFailure(type)
        is JmapException.Unreachable -> SendFailure.Unreachable
        else -> SendFailure.ServerProblem
    }

private fun sendFailure(type: String): SendFailure =
    when (type) {
        "noRecipients", "invalidRecipients" -> SendFailure.Recipients
        "tooManyRecipients" -> SendFailure.TooManyRecipients
        "tooLarge" -> SendFailure.TooLarge
        "forbidden", "forbiddenFrom", "forbiddenMailFrom", "forbiddenToSend" -> SendFailure.NotPermitted
        else -> SendFailure.ServerProblem
    }

internal const val ANSWERED_KEYWORD = "\$answered"
internal const val FORWARDED_KEYWORD = "\$forwarded"
