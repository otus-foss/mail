package com.otus.mail.feature.inbox.domain

/**
 * Folders [added] to and [removed] from every email of a conversation, which the list shows as
 * [row]. [before] holds each email's mailboxes by Email id. JMAP keeps an email only while it is
 * in at least one mailbox, so the change applies only when every email keeps one.
 */
data class FolderChange(
    val row: MailMessage,
    val before: Map<String, Set<String>>,
    val added: Set<String>,
    val removed: Set<String>,
) {
    /** Each email's mailboxes after the change. */
    val after: Map<String, Set<String>> = before.mapValues { (_, boxes) -> boxes - removed + added }

    val changes: Boolean get() = added.isNotEmpty() || removed.isNotEmpty()

    /** Whether every email stays in a folder, which the server requires. */
    val keepsEveryEmail: Boolean get() = after.values.none { it.isEmpty() }

    /** Whether the row leaves the list, because it was listed in a folder the change removes. */
    val leavesList: Boolean get() = row.location.mailboxId in removed
}

/**
 * Each email's mailboxes by Email id: the row's listed emails for a conversation, else its own.
 * Supplied messages have no server id and give none.
 */
val MailMessage.memberMailboxes: Map<String, Set<String>>
    get() =
        (conversation?.members?.associate { it.emailId to it.mailboxIds } ?: mapOf(emailId to location.mailboxIds))
            .filterKeys { it.isNotEmpty() }

/** This row with each email of [mailboxes], by Email id, in exactly those mailboxes. */
fun MailMessage.withMailboxes(mailboxes: Map<String, Set<String>>): MailMessage =
    copy(
        location = location.copy(mailboxIds = mailboxes[emailId] ?: location.mailboxIds),
        conversation =
            conversation?.let { thread ->
                val members = thread.members.map { it.copy(mailboxIds = mailboxes[it.emailId] ?: it.mailboxIds) }
                thread.copy(members = members)
            },
    )
