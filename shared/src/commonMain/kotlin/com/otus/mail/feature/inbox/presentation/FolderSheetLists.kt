package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.MessageFolder
import com.otus.mail.feature.inbox.domain.orderedFolders

/** The Folders sheet's two lists: the folders a message is [current]ly in, then [others] it can join. */
data class FolderSheetLists(
    val current: List<MessageFolder>,
    val others: List<MessageFolder>,
)

/**
 * The folders of [folders] that any email of a conversation is in, [inIds], and the rest, each
 * inbox first and then by path. Drafts and Read later stay out, since the bookmark shows Read
 * later. Spam and Trash are places mail moves to, not folders it is added to, so they are listed
 * only when the mail is in them.
 */
fun folderSheetLists(
    inIds: Set<String>,
    folders: List<MessageFolder>,
): FolderSheetLists {
    val byId = folders.associateBy { it.id }
    val shown = { ids: Set<String> -> orderedFolders(ids, byId).filterNot { it.role == DRAFTS || it.readLater } }
    return FolderSheetLists(
        current = shown(inIds),
        others = shown(byId.keys - inIds).filterNot { it.role in DESTINATIONS },
    )
}

/** Whether the folder's path contains [query], ignoring case; a blank query matches. */
fun MessageFolder.matchesSearch(query: String): Boolean = path.joinToString(" / ").containsSearch(query)

private const val DRAFTS = "drafts"
private val DESTINATIONS = setOf("junk", "trash")
