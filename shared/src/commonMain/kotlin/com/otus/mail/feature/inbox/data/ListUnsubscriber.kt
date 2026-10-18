package com.otus.mail.feature.inbox.data

import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.DraftRepository
import com.otus.mail.feature.inbox.domain.DraftSender
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.Unsubscribe
import com.otus.mail.feature.inbox.domain.UnsubscribeResult
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.URLDecodeException
import io.ktor.http.URLParserException
import io.ktor.http.content.TextContent
import io.ktor.http.decodeURLPart
import io.ktor.http.isSuccess
import kotlinx.io.IOException

/**
 * Leaves a mailing list the best way its headers allow: the RFC 8058 one-click POST, then an email
 * to its `mailto:` address (RFC 6068) through the account's own send path, then the web page for
 * the user to finish. Each way that fails falls through to the next.
 */
internal class ListUnsubscriber(
    private val http: HttpClient,
    private val drafts: DraftRepository,
    private val sender: DraftSender,
) {
    suspend fun unsubscribe(
        message: MailMessage,
        how: Unsubscribe,
    ): UnsubscribeResult =
        when {
            how.oneClick?.let { oneClick(it) } == true -> UnsubscribeResult.Done
            how.mail?.let { mail(message, it) } == true -> UnsubscribeResult.Done
            how.web != null -> UnsubscribeResult.OpenLink(how.web)
            else -> UnsubscribeResult.Failed
        }

    /** Posts `List-Unsubscribe=One-Click` as a form (RFC 8058, section 3.1); only to https addresses. */
    private suspend fun oneClick(url: String): Boolean {
        if (!url.startsWith("https://", ignoreCase = true)) return false
        return try {
            http
                .post(url) { setBody(TextContent(ONE_CLICK_BODY, ContentType.Application.FormUrlEncoded)) }
                .status
                .isSuccess()
        } catch (expected: IOException) {
            false
        } catch (expected: URLParserException) {
            false
        }
    }

    /** Sends the mail a `mailto:` address asks for, from the identity the message was sent to. */
    private suspend fun mail(
        message: MailMessage,
        address: String,
    ): Boolean {
        val mailto = parseMailto(address)
        val from = mailto?.let { sendingAs(message) }
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
     * The address the list wrote to, else the account's own address, else any. Wildcard
     * identities, such as `*@example.org`, cannot be a sender address.
     */
    private suspend fun sendingAs(message: MailMessage): MailSender? {
        val identities =
            drafts.identities(message.location.accountId).map { it.sender }.filterNot { '*' in it.address }
        val received = (message.to + message.cc).map { it.address.lowercase() }.toSet()
        return identities.firstOrNull { it.address.lowercase() in received }
            ?: identities.firstOrNull { it.address.equals(message.location.accountAddress, ignoreCase = true) }
            ?: identities.firstOrNull()
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
private const val ONE_CLICK_BODY = "List-Unsubscribe=One-Click"

/** The subject when the `mailto:` address names none; list servers act on the address alone. */
private const val DEFAULT_SUBJECT = "unsubscribe"
