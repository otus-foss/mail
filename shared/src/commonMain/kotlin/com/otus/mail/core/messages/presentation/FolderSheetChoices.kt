package com.otus.mail.core.messages.presentation

import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.mailbox.pathText
import com.otus.mail.core.messages.domain.MessageFolder

/**
 * What the Folders sheet offers, as the Move to sheet does: the [system] folders as pills, then the
 * user's [own] folders as a tree.
 */
data class FolderSheetChoices(
    val system: List<MessageFolder>,
    val own: List<MessageFolder>,
)

/**
 * [folders], in their tree order, split into the system folders and Read later, which show as
 * pills as the Move to sheet's do, Inbox first and Read later next, and the user's own folders.
 * Drafts and Scheduled stay out, since mail is not added to them.
 */
fun folderSheetChoices(folders: List<MessageFolder>): FolderSheetChoices {
    val listed = folders.filter { it.role?.holdsUnsent != true }
    val (system, own) = listed.partition { it.role != null || it.readLater }
    val (inbox, rest) = system.partition { it.role == MailboxRole.Inbox }
    val (readLater, others) = rest.partition { it.readLater }
    return FolderSheetChoices(inbox + readLater + others, own)
}

/** A folder in the Folders sheet's tree: how deep it nests, and the [parents] shown before its name. */
data class SheetTreeFolder(
    val folder: MessageFolder,
    val depth: Int,
    val parents: List<String>,
)

/**
 * [folders], in tree order, nested under one another by path. A folder whose parent is not in
 * [folders], because the mail is already in it or it is hidden, becomes a root that keeps its
 * parents' names, so it does not pass for a top-level folder.
 */
fun sheetTree(folders: List<MessageFolder>): List<SheetTreeFolder> {
    val paths = folders.map { it.path }.toSet()
    val hasListedParent = { path: List<String> -> path.size > 1 && path.dropLast(1) in paths }
    val depth = { path: List<String> ->
        generateSequence(path) { child -> child.dropLast(1).takeIf { hasListedParent(child) } }.count() - 1
    }
    return folders.map { folder ->
        val parents =
            folder.path
                .dropLast(1)
                .takeUnless { hasListedParent(folder.path) }
                .orEmpty()
        SheetTreeFolder(folder, depth(folder.path), parents)
    }
}

/** Whether the folder's path contains [query], ignoring case; a blank query matches. */
fun MessageFolder.matchesSearch(query: String): Boolean = path.pathText().containsSearch(query)
