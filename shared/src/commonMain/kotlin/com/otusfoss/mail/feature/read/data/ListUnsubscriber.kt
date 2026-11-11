package com.otusfoss.mail.feature.read.data

import com.otusfoss.core.avatar.AvatarDomainResolver
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.DraftRepository
import com.otusfoss.mail.core.messages.domain.DraftSender
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.SendTime
import com.otusfoss.mail.core.messages.domain.Unsubscribe
import com.otusfoss.mail.core.messages.domain.displayLine
import com.otusfoss.mail.core.messages.domain.mailtoRequest
import com.otusfoss.mail.core.messages.domain.sendingAs
import com.otusfoss.mail.feature.read.domain.UnsubscribeResult

/**
 * Sends to the list's `mailto:` address through the account, then falls back to its web page
 * when the address fails the checks in [mail].
 * Automatic RFC 8058 POSTs require trusted DKIM verification that the current JMAP path lacks.
 */
internal class ListUnsubscriber(
    private val drafts: DraftRepository,
    private val sender: DraftSender,
    private val domains: AvatarDomainResolver,
) {
    suspend fun unsubscribe(
        message: MailMessage,
        how: Unsubscribe,
    ): UnsubscribeResult =
        when {
            how.mail?.let { mail(message, it, how) } == true -> UnsubscribeResult.Done
            how.web != null -> UnsubscribeResult.OpenLink(how.web)
            else -> UnsubscribeResult.Failed
        }

    /**
     * Sends one plain request, from the identity the message was sent to, to the list address the
     * `mailto:` link names. The sender writes the link, so only its subject is kept; its body and
     * any further addresses are dropped, and the address must share the list's or sender's domain.
     */
    private suspend fun mail(
        message: MailMessage,
        link: String,
        how: Unsubscribe,
    ): Boolean {
        val request = mailtoRequest(link)
        val to = request?.let { listAddress(link, message, how.listId) }
        val from = to?.let { sendingAs(message, how.deliveredTo) }
        if (request == null || to == null || from == null) return false
        val accountId = message.location.accountId
        val draft =
            Draft(
                accountId = accountId,
                from = from,
                to = listOf(MailSender(to, to, accountId)),
                subject = request.subject.displayLine().ifBlank { DEFAULT_SUBJECT },
            )
        return sender.send(draft, answered = null, SendTime.Now).isSuccess
    }

    /**
     * The first address before the link's `?` on the registrable domain of the [listId] or the
     * message's sender. `to`, `cc`, and `bcc` fields are ignored, so a link cannot add recipients.
     */
    private suspend fun listAddress(
        link: String,
        message: MailMessage,
        listId: String?,
    ): String? {
        val trusted =
            listOfNotNull(listId, message.sender.address.substringAfterLast('@', ""))
                .mapNotNull { registrableDomain(it) }
        return mailtoRequest(link.substringBefore('?'))
            ?.to
            ?.map { it.address.trim() }
            ?.firstOrNull { address ->
                val domain = registrableDomain(address.substringAfterLast('@', ""))
                domain != null && domain in trusted
            }
    }

    private suspend fun registrableDomain(host: String): String? =
        host
            .trim()
            .trimEnd('.')
            .lowercase()
            .ifEmpty { null }
            ?.let { domains.resolve(it) }
            ?.lowercase()

    /**
     * The address the list reached: the one the mail was [deliveredTo], else one in its To or Cc,
     * when an identity sends as it. A wildcard identity, such as `*@example.org`, sends as the exact
     * address under it, so a catch-all alias stays the only address the list knows. Null when none
     * matches: falling back to the sign-in address or another alias would tell the list about it.
     */
    private suspend fun sendingAs(
        message: MailMessage,
        deliveredTo: List<String>,
    ): MailSender? {
        val identities = drafts.identities(message.location.accountId).map { it.sender }
        return (deliveredTo + (message.to + message.cc).map { it.address })
            .map { it.trim() }
            .firstNotNullOfOrNull { address ->
                identities.sendingAs(address) { it.address }?.copy(address = address.lowercase())
            }
    }
}

/** The subject when the `mailto:` address names none; list servers act on the address alone. */
private const val DEFAULT_SUBJECT = "unsubscribe"
