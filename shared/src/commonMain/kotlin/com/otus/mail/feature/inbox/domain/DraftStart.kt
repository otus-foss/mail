package com.otus.mail.feature.inbox.domain

/** The account a draft for this request comes from, when the request names one. */
val ComposeRequest.accountId: String?
    get() =
        when (this) {
            is ComposeRequest.New -> accountId
            is ComposeRequest.Reply -> original.accountId
            is ComposeRequest.FollowUp -> original.accountId
            is ComposeRequest.Forward -> original.accountId
        }

/**
 * The draft this request starts in [accountId]: blank, a reply that quotes the original, or a
 * forward that carries it. Reply all leaves out [ownAddresses], the account's own addresses.
 */
fun ComposeRequest.startDraft(
    accountId: String,
    ownAddresses: Collection<String>,
): Draft =
    when (this) {
        is ComposeRequest.New -> {
            Draft(accountId)
        }

        is ComposeRequest.Reply -> {
            original.reply(accountId, all, ownAddresses.map(String::lowercase).toSet())
        }

        is ComposeRequest.FollowUp -> {
            original.reply(accountId, all = true, ownAddresses.map(String::lowercase).toSet(), fromSelf = true)
        }

        is ComposeRequest.Forward -> {
            Draft(
                accountId,
                subject = original.subject.prefixed(FORWARD_PREFIX, forwardPattern),
                body = original.forwardBody(),
            )
        }
    }

/**
 * The identity to send from: for a reply or forward, the one the original went to, so the
 * answer comes from the address the sender wrote to; for a follow-up, the one that sent the
 * original; otherwise the first.
 */
fun ComposeRequest.preferredIdentity(identities: List<MailSender>): MailSender? {
    val senders =
        when (this) {
            is ComposeRequest.New -> emptyList()
            is ComposeRequest.Reply -> original.to + original.cc
            is ComposeRequest.FollowUp -> listOf(original.from)
            is ComposeRequest.Forward -> original.to + original.cc
        }
    val addresses = senders.map { it.address.lowercase() }
    return identities.firstOrNull { it.address.lowercase() in addresses } ?: identities.firstOrNull()
}

/**
 * A reply that goes to [from], or [fromSelf] to the recipients of a message the account sent. A
 * follow-up is always [fromSelf], even when it was sent from an address the account doesn't list.
 */
private fun OriginalMessage.reply(
    accountId: String,
    all: Boolean,
    own: Set<String>,
    fromSelf: Boolean = from.address.lowercase() in own,
): Draft {
    val primary = if (fromSelf) to else listOf(from)
    val to = if (all && !fromSelf) primary + to else primary
    val kept =
        (to + if (all) cc else emptyList()).distinctBy { it.address.lowercase() }.filterNot {
            it.address.lowercase() in
                own
        }
    val toAddresses = to.map { it.address.lowercase() }.toSet()
    return Draft(
        accountId,
        to = kept.filter { it.address.lowercase() in toAddresses },
        cc = kept.filterNot { it.address.lowercase() in toAddresses },
        subject = subject.prefixed(REPLY_PREFIX, replyPattern),
        body = replyBody(),
        inReplyTo = messageIds,
        references = (references + messageIds).distinct(),
    )
}

private fun OriginalMessage.replyBody(): String =
    buildList {
        add("")
        add("")
        add("On $sentLabel, ${from.displayName} wrote:")
        addAll(body.lines().map { if (it.isEmpty()) QUOTE else "$QUOTE $it" })
        if (bodyTruncated) add("$QUOTE $TRUNCATED_NOTE")
    }.joinToString("\n")

private fun OriginalMessage.forwardBody(): String =
    buildList {
        add("")
        add("")
        add("---------- Forwarded message ----------")
        add("From: ${from.recipientText()}")
        add("Date: $sentLabel")
        add("Subject: $subject")
        add("To: ${to.recipientText()}")
        if (cc.isNotEmpty()) add("Cc: ${cc.recipientText()}")
        add("")
        add(body)
        if (bodyTruncated) add(TRUNCATED_NOTE)
    }.joinToString("\n")

private val MailSender.displayName: String
    get() = name.ifBlank { address }

/** Adds [prefix] unless the subject already starts with one that [existing] matches. */
private fun String.prefixed(
    prefix: String,
    existing: Regex,
): String = if (existing.containsMatchIn(this)) this else "$prefix $this".trimEnd()

private const val REPLY_PREFIX = "Re:"
private const val FORWARD_PREFIX = "Fwd:"
private const val QUOTE = ">"

/** Ends a quoted or forwarded body that stops early, so no reader takes it for the whole message. */
private const val TRUNCATED_NOTE = "[The rest of the original message is not included.]"

private val replyPattern = Regex("^\\s*re\\s*:", RegexOption.IGNORE_CASE)
private val forwardPattern = Regex("^\\s*(fwd?|fw)\\s*:", RegexOption.IGNORE_CASE)
