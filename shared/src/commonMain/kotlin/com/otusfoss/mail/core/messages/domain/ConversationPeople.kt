package com.otusfoss.mail.core.messages.domain

import com.otusfoss.mail.core.mail.EmailAddressKey

/**
 * The person a row names and how many other people take part: everyone who wrote in the thread,
 * and the recipients of the user's own newest email. [others] never counts the user. When
 * the user wrote the newest email, the row names its first To recipient who is someone else, then
 * any other participant, and [SELF_NAME] for a note to self. A message without a conversation uses
 * its own sender and recipients.
 */
fun MailMessage.senderLine(own: OwnAddresses): SenderLine {
    val last = conversation?.last ?: ThreadEmail(sender, to, received)
    val fromUser = isOwn(own, last.sender)
    val wrote = conversation?.senders ?: listOf(sender)
    val addressed = if (fromUser) conversation?.let { last.to } ?: (to + cc) else emptyList()
    val others = (wrote + addressed).distinctBy { it.key }.filterNot { isOwn(own, it) }
    return if (fromUser) {
        val named = last.to.firstOrNull { !isOwn(own, it) } ?: others.firstOrNull()
        SenderLine.YouTo(named?.shownName ?: SELF_NAME, others.count { it.key != named?.key })
    } else {
        SenderLine.From(last.sender.shownName, others.count { it.key != last.sender.key })
    }
}

/**
 * The user's address this message reached: the first of its To and Cc recipients that is the
 * user's own, else the account's sign-in address; null when neither is known.
 */
fun MailMessage.receivedOn(own: OwnAddresses): String? =
    (to + cc).firstOrNull { own.isMine(location.accountId, it.address) }?.address
        ?: location.accountAddress.ifBlank { null }

/**
 * [subject] without the reply and forward prefixes mail apps add, in English and German, also
 * repeated or counted ("Re: Re:", "Re[2]:"), so every email of a thread shows one subject.
 */
fun threadSubject(subject: String): String = subject.replace(REPLY_PREFIXES, "").trim()

/** The sign-in address counts as the user's own even before the identities load. */
internal fun MailMessage.isOwn(
    own: OwnAddresses,
    person: MailSender,
): Boolean =
    own.isMine(location.accountId, person.address) ||
        (person.address.isNotBlank() && EmailAddressKey(person.address) == EmailAddressKey(location.accountAddress))

private val MailSender.key: String
    get() = EmailAddressKey(address).value.ifBlank { name }

/** Names the recipient of a note the user wrote only to themselves. */
const val SELF_NAME = "yourself"

private val REPLY_PREFIXES = Regex("""(?i)^\s*(?:(?:re|fwd|fw|aw|wg|antw)\s*(?:\[\d+]|\(\d+\))?\s*:\s*)+""")
