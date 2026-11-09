package com.otus.mail.feature.compose.domain

import com.otus.mail.core.mail.EmailAddressKey
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.Draft
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.OriginalMessage
import com.otus.mail.core.messages.domain.accountId
import com.otus.mail.core.messages.domain.matching
import com.otus.mail.core.messages.domain.original
import com.otus.mail.core.messages.domain.recipientText
import com.otus.mail.core.messages.domain.shownName

/**
 * The draft this request starts in [accountId]: a new message as the request fills it in, a reply
 * that quotes the original, or a forward that carries it. Reply all leaves out [ownAddresses], the
 * account's own addresses.
 */
fun ComposeRequest.startDraft(
    accountId: String,
    ownAddresses: Collection<String>,
): Draft =
    when (this) {
        is ComposeRequest.New -> Draft(accountId, to = to, cc = cc, bcc = bcc, subject = subject, body = body)
        is ComposeRequest.Reply -> original.reply(accountId, all, ownAddresses.map { EmailAddressKey(it) }.toSet())
        is ComposeRequest.FollowUp -> original.followUp(accountId, ownAddresses)
        is ComposeRequest.Forward -> original.forward(accountId)
        is ComposeRequest.Resume -> draft
    }

private fun OriginalMessage.followUp(
    accountId: String,
    ownAddresses: Collection<String>,
): Draft = reply(accountId, all = true, ownAddresses.map { EmailAddressKey(it) }.toSet(), fromSelf = true)

private fun OriginalMessage.forward(accountId: String): Draft =
    Draft(accountId, subject = subject.prefixed(FORWARD_PREFIX, forwardPattern), quote = forwardBody())

/**
 * The identity to send from, among every account's [identities]. A reply or forward comes from the
 * address the original went to, and a follow-up from the one that sent it, preferring the
 * original's account. A new message from one account comes from that account; one from all
 * accounts comes from the first of the [recent] addresses that is still an identity. Otherwise
 * the account's first identity, or the first of all.
 */
fun ComposeRequest.preferredIdentity(
    identities: List<MailSender>,
    recent: List<String> = emptyList(),
): MailSender? {
    if (this is ComposeRequest.Resume) return draft.from ?: from.preferredIdentity(identities, recent)
    val home = accountId
    val addressed = addressedTo().map { EmailAddressKey(it.address) }.toSet()
    val matching = identities.filter { EmailAddressKey(it.address) in addressed }
    val lastUsed =
        recent.firstNotNullOfOrNull { address ->
            identities.firstOrNull { it.address.equals(address, ignoreCase = true) }
        }
    return matching.firstOrNull { it.accountKey == home }
        ?: matching.firstOrNull()
        ?: lastUsed.takeIf { this is ComposeRequest.New && home == null }
        ?: identities.firstOrNull { it.accountKey == home }
        ?: identities.firstOrNull()
}

/** The people the original went to, whose address a reply or forward is sent from. */
private fun ComposeRequest.addressedTo(): List<MailSender> =
    when (this) {
        is ComposeRequest.New -> emptyList()
        is ComposeRequest.Reply -> original.to + original.cc
        is ComposeRequest.FollowUp -> listOf(original.from)
        is ComposeRequest.Forward -> original.to + original.cc
        is ComposeRequest.Resume -> from.addressedTo()
    }

/**
 * A reply that goes to [from], or [fromSelf] to the recipients of a message the account sent. A
 * follow-up is always [fromSelf], even when it was sent from an address the account doesn't list.
 */
private fun OriginalMessage.reply(
    accountId: String,
    all: Boolean,
    own: Set<EmailAddressKey>,
    fromSelf: Boolean = EmailAddressKey(from.address) in own,
): Draft {
    val primary = if (fromSelf) to else replyTo.ifEmpty { listOf(from) }
    val recipients = if (all && !fromSelf) primary + to else primary
    val kept =
        (recipients + if (all) cc else emptyList())
            .distinctBy { EmailAddressKey(it.address) }
            .filterNot { EmailAddressKey(it.address) in own }
    val toAddresses = recipients.map { EmailAddressKey(it.address) }.toSet()
    return Draft(
        accountId,
        to = kept.filter { EmailAddressKey(it.address) in toAddresses },
        cc = kept.filterNot { EmailAddressKey(it.address) in toAddresses },
        subject = subject.prefixed(REPLY_PREFIX, replyPattern),
        quote = replyBody(),
        inReplyTo = messageIds,
        references = (references + messageIds).distinct(),
    )
}

private fun OriginalMessage.replyBody(): String = quoted(body)

/** [text] of this message quoted line by line under "On <date>, <name> wrote:". */
internal fun OriginalMessage.quoted(text: String): String =
    buildList {
        add("On $sentLabel, ${from.shownName} wrote:")
        addAll(text.lines().map { if (it.isEmpty()) QUOTE else "$QUOTE $it" })
        if (bodyTruncated && text == body) add("$QUOTE $TRUNCATED_NOTE")
    }.joinToString("\n")

private fun OriginalMessage.forwardBody(): String =
    buildList {
        add(FORWARD_HEADER)
        add("From: ${from.recipientText()}")
        add("Date: $sentLabel")
        add("Subject: $subject")
        add("To: ${to.recipientText()}")
        if (cc.isNotEmpty()) add("Cc: ${cc.recipientText()}")
        add("")
        add(body)
        if (bodyTruncated) add(TRUNCATED_NOTE)
    }.joinToString("\n")

/** Adds [prefix] unless the subject already starts with one that [existing] matches. */
private fun String.prefixed(
    prefix: String,
    existing: Regex,
): String = if (existing.containsMatchIn(this)) this else "$prefix $this".trimEnd()

private const val REPLY_PREFIX = "Re:"

private const val FORWARD_PREFIX = "Fwd:"

internal const val QUOTE = ">"

/** The line [quoted] puts before a quote, which a saved draft's quote starts with. */
internal val QUOTE_INTRO = Regex("^On .+ wrote:$")

/** The line a forward's copy of the original starts with. */
internal const val FORWARD_HEADER = "---------- Forwarded message ----------"

/** Ends a quoted or forwarded body that stops early, so no reader takes it for the whole message. */
private const val TRUNCATED_NOTE = "[The rest of the original message is not included.]"

private val replyPattern = Regex("^\\s*re\\s*:", RegexOption.IGNORE_CASE)

private val forwardPattern = Regex("^\\s*(fwd?|fw)\\s*:", RegexOption.IGNORE_CASE)
