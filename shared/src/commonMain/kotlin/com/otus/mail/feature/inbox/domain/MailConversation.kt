package com.otus.mail.feature.inbox.domain

/**
 * A JMAP thread as one list row. [members] are its emails in the shown mailboxes, the row email
 * first. Actions on the row apply to every member, so the whole conversation leaves or changes.
 */
data class MailConversation(
    val threadId: String,
    /** Emails in the thread, without drafts, and without Trash or Junk unless one of those is shown. */
    val messageCount: Int,
    /** The newest counted email of the whole thread, Sent included, which dates the row and names its sender. */
    val last: ThreadEmail,
    /**
     * Who wrote the counted emails, each address once, newest email first. Recipients who never
     * wrote stay out, so a mail to a whole list does not count everyone on it.
     */
    val senders: List<MailSender>,
    /** Whether any counted email has the `$answered` keyword. */
    val answered: Boolean,
    val members: List<ThreadMember>,
    /**
     * Whether both sides wrote: the user at least once (an email in Sent) and someone else at least
     * once (an email outside Sent). Only such threads count as conversations; Sent alone does not.
     */
    val exchanged: Boolean = false,
)

/** Who sent one email of a thread, to whom, and when. */
data class ThreadEmail(
    val sender: MailSender,
    val to: List<MailSender>,
    val received: ReceivedAt,
)

/**
 * One email of a conversation row. [mailboxIds] are all of its mailboxes; [mailboxId] is the shown
 * mailbox it was listed in, which Delete and Not spam take it out of.
 */
data class ThreadMember(
    val emailId: String,
    val mailboxIds: Set<String>,
    val mailboxId: String = "",
)
