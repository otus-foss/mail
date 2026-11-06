package com.otus.mail.core.messages.data

import com.otus.mail.core.mail.EmailAddressKey
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.isDraft

/**
 * The mailboxes a list [shown], and its account's Trash and Junk mailboxes. A thread counts a
 * draft or an email in Trash or Junk only when it is in a shown mailbox, so a conversation in the
 * Inbox does not count deleted replies, while the Trash list counts them. [own] holds the login's
 * addresses, its identities and sign-in address, which tell the user's emails apart.
 */
internal class ThreadView(
    private val shown: Set<String>,
    private val trashAndJunk: Set<String>,
    private val sent: Set<String> = emptySet(),
    own: Set<String> = emptySet(),
) {
    private val own = own.map { EmailAddressKey(it) }.toSet()

    /** Whether the user sent [email]: it is in the account's Sent folder. */
    fun isSent(email: EmailObject): Boolean = email.boxes.any { it in sent }

    /** Whether the user wrote [email]: it is from one of their addresses, or in Sent. */
    fun isMine(email: EmailObject): Boolean =
        isSent(email) || email.from.orEmpty().any { EmailAddressKey(it.email) in own }

    /** Whether one of the user's addresses is in [email]'s To field, not only in Cc. */
    fun isToMe(email: EmailObject): Boolean = email.to.orEmpty().any { EmailAddressKey(it.email) in own }

    fun counts(email: EmailObject): Boolean {
        val boxes = email.boxes
        return boxes.any { it in shown } ||
            (!email.isDraft && boxes.none { it in trashAndJunk })
    }
}
