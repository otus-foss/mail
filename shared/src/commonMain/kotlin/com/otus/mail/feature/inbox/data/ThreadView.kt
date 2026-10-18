package com.otus.mail.feature.inbox.data

import com.otus.mail.core.mail.EmailObject

/**
 * The mailboxes a list [shown], and its account's Trash and Junk mailboxes. A thread counts a
 * draft or an email in Trash or Junk only when it is in a shown mailbox, so a conversation in the
 * Inbox does not count deleted replies, while the Trash list counts them.
 */
internal class ThreadView(
    private val shown: Set<String>,
    private val trashAndJunk: Set<String>,
    private val sent: Set<String> = emptySet(),
) {
    /** Whether the user sent [email]: it is in the account's Sent folder. */
    fun isSent(email: EmailObject): Boolean = email.boxes.any { it in sent }

    fun counts(email: EmailObject): Boolean {
        val boxes = email.boxes
        return boxes.any { it in shown } ||
            (email.keywords[DRAFT_KEYWORD] != true && boxes.none { it in trashAndJunk })
    }
}
