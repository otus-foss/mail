package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFolder
import com.otus.mail.feature.inbox.domain.folderHint

/** The Folders sheet's two lists: the folders a message is [current]ly in, then [others] it can join. */
data class FolderSheetLists(
    val current: List<MessageFolder>,
    val others: List<MessageFolder>,
)

/**
 * The folders of [folders] that [message] is in, and the rest, each inbox first and then by path.
 * Drafts and Read later stay out, since the bookmark shows Read later. Spam and Trash are places
 * mail moves to, not folders it is added to, so they are listed only when the mail is in them.
 */
fun folderSheetLists(
    message: MailMessage,
    folders: List<MessageFolder>,
): FolderSheetLists {
    val byId = folders.associateBy { it.id }
    val inIds = message.everyMailboxId
    return FolderSheetLists(
        current = folderHint(inIds, byId, cameFrom = null).folders,
        others = folderHint(byId.keys - inIds, byId, cameFrom = null).folders.filterNot { it.role in DESTINATIONS },
    )
}

/** Every mailbox of the message, or of every email of the conversation it stands for. */
val MailMessage.everyMailboxId: Set<String>
    get() =
        conversation
            ?.members
            ?.flatMap { it.mailboxIds }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: location.mailboxIds

/** Whether the folder's path contains [query], ignoring case; a blank query matches. */
fun MessageFolder.matchesSearch(query: String): Boolean = path.joinToString(" / ").containsSearch(query)

private val DESTINATIONS = setOf("junk", "trash")
