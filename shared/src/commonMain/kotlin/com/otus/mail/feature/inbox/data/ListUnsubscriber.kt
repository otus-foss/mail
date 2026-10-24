package com.otus.mail.feature.inbox.data

import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.DraftRepository
import com.otus.mail.feature.inbox.domain.DraftSender
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.Unsubscribe
import com.otus.mail.feature.inbox.domain.UnsubscribeResult
import io.ktor.http.URLDecodeException
import io.ktor.http.decodeURLPart

/**
 * Sends to the list's `mailto:` address through the account, then falls back to its web page.
 * Automatic RFC 8058 POSTs require trusted DKIM verification that the current JMAP path lacks.
 */
internal class ListUnsubscriber(
    private val drafts: DraftRepository,
    private val sender: DraftSender,
) {
    suspend fun unsubscribe(
        message: MailMessage,
        how: Unsubscribe,
    ): UnsubscribeResult =
        when {
            how.mail?.let { mail(message, it, how.deliveredTo) } == true -> UnsubscribeResult.Done
            how.web != null -> UnsubscribeResult.OpenLink(how.web)
            else -> UnsubscribeResult.Failed
        }

    /** Sends the mail a `mailto:` address asks for, from the identity the message was sent to. */
    private suspend fun mail(
        message: MailMessage,
        address: String,
        deliveredTo: List<String>,
    ): Boolean {
        val mailto = parseMailto(address)
        val from = mailto?.let { sendingAs(message, deliveredTo) }
        if (mailto == null || from == null) return false
        val accountId = message.location.accountId
        val draft =
            Draft(
                accountId = accountId,
                from = from,
                to = mailto.to.map { MailSender(it, it, accountId) },
                subject = mailto.subject ?: DEFAULT_SUBJECT,
                body = mailto.body.orEmpty(),
            )
        return sender.send(draft, answered = null, at = null).isSuccess
    }

    /**
     * The identity the list reached: the address the mail was [deliveredTo], else one in its To or
     * Cc, else the account's sign-in address. Never another alias, since the list would learn it.
     * Wildcard identities, such as `*@example.org`, cannot be a sender address.
     */
    private suspend fun sendingAs(
        message: MailMessage,
        deliveredTo: List<String>,
    ): MailSender? {
        val identities =
            drafts.identities(message.location.accountId).map { it.sender }.filterNot { '*' in it.address }
        val byAddress = identities.associateBy { it.address.lowercase() }
        return (deliveredTo + (message.to + message.cc).map { it.address } + message.location.accountAddress)
            .firstNotNullOfOrNull { byAddress[it.trim().lowercase()] }
    }
}

/** The addresses, subject, and body of a `mailto:` URI (RFC 6068). */
internal data class Mailto(
    val to: List<String>,
    val subject: String?,
    val body: String?,
)

/** Null when [uri] is not a `mailto:` URI with an address, or its escapes are broken. */
internal fun parseMailto(uri: String): Mailto? {
    if (!uri.startsWith(MAILTO, ignoreCase = true)) return null
    return try {
        val rest = uri.substring(MAILTO.length)
        val fields =
            rest
                .substringAfter('?', missingDelimiterValue = "")
                .split('&')
                .filter { '=' in it }
                .associate { it.substringBefore('=').lowercase() to it.substringAfter('=').decodeURLPart() }
        val to =
            (rest.substringBefore('?').decodeURLPart().split(',') + fields["to"].orEmpty().split(','))
                .map { it.trim() }
                .filter { '@' in it }
        Mailto(to, fields["subject"], fields["body"]).takeIf { to.isNotEmpty() }
    } catch (expected: URLDecodeException) {
        null
    }
}

private const val MAILTO = "mailto:"

/** The subject when the `mailto:` address names none; list servers act on the address alone. */
private const val DEFAULT_SUBJECT = "unsubscribe"
