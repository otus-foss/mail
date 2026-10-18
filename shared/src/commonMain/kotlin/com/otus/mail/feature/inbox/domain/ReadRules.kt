package com.otus.mail.feature.inbox.domain

/**
 * Whether a message's remote images load before the user asks. Never in Spam, whatever the
 * [setting]. With [RemoteImages.Known], only when the [sender] is in [known] (contacts and people
 * the user wrote to) or in [allowed]. Addresses compare without case.
 */
fun showsRemoteImages(
    setting: RemoteImages,
    sender: String,
    known: Set<String>,
    allowed: List<String>,
    inSpam: Boolean,
): Boolean {
    val address = sender.trim().lowercase()
    return when {
        inSpam -> false
        setting == RemoteImages.Always -> true
        setting == RemoteImages.Never -> false
        else -> known.any { it.lowercase() == address } || allowed.any { it.trim().lowercase() == address }
    }
}

/**
 * The reply the Read view's big button offers: [MailAction.ReplyAll] when more than one other
 * person is on the message, else [MailAction.Reply]. Other people are the sender and the To and
 * Cc recipients, each address once, without the user's own addresses.
 */
fun MailMessage.replyAction(own: OwnAddresses): MailAction =
    if (otherPeople(own).size > 1) MailAction.ReplyAll else MailAction.Reply

/**
 * The reply the big button does not cover, for the ⋯ menu: Reply to the sender while the button
 * says Reply all. Null while it says Reply, because with one other person Reply all is the same.
 */
fun MailMessage.otherReply(own: OwnAddresses): MailAction? =
    MailAction.Reply.takeIf { replyAction(own) == MailAction.ReplyAll }

/** Everyone on this message except the user, each address once. */
fun MailMessage.otherPeople(own: OwnAddresses): List<MailSender> =
    (listOf(sender) + to + cc)
        .filter { it.address.isNotBlank() && !isOwn(own, it) }
        .distinctBy { it.address.trim().lowercase() }
