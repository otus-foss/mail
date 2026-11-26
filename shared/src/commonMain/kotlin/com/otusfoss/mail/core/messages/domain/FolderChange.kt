package com.otusfoss.mail.core.messages.domain

import com.otusfoss.mail.core.mailbox.MailboxRole

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

/**
 * The folders a move to [shortcut] (or a regular folder when null) takes this row out of. Trash
 * and Spam leave every folder of the row. Archive leaves the listed folders outside Sent, Drafts,
 * Scheduled, Trash, and Spam, plus [inboxId] when the row is in it. Other moves leave those same
 * listed folders, or only the shown bin folder when the row is shown in Trash or Spam. Falls back
 * to the shown folder when none else applies.
 */
fun MailMessage.leavingFolders(
    shortcut: MailboxRole?,
    inboxId: String? = null,
    roleOf: (String) -> MailboxRole? = { null },
): Set<String> {
    val shown = setOf(location.mailboxId) - ""
    val folderIds = location.mailboxIds
    val shownRole = location.mailboxRole ?: roleOf(location.mailboxId)
    return when (shortcut) {
        MailboxRole.Trash, MailboxRole.Junk -> shown + folderIds
        MailboxRole.Archive -> filingFolders(shown, roleOf) + setOfNotNull(inboxId?.takeIf { it in folderIds })
        else -> if (shownRole?.isBin == true) shown else filingFolders(shown, roleOf)
    }
}

private fun MailMessage.filingFolders(
    shown: Set<String>,
    roleOf: (String) -> MailboxRole?,
): Set<String> {
    val listed =
        conversation
            ?.members
            ?.filter { member ->
                val role = member.mailboxRole ?: roleOf(member.mailboxId)
                role?.isBin != true && role?.holdsOwnMail != true
            }?.map { it.mailboxId }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()
    return listed.ifEmpty { shown }
}
