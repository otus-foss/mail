package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.MessageFolder

/**
 * What the Folders sheet offers, as the Move to sheet does: the [system] folders as pills, then the
 * user's [own] folders as a tree.
 */
data class FolderSheetChoices(
    val system: List<MessageFolder>,
    val own: List<MessageFolder>,
)

/**
 * [folders], in their tree order, split into system folders and the user's own. Drafts and Read
 * later stay out, since the bookmark shows Read later. Spam and Trash are places mail moves to,
 * not folders it is added to, so they show only when an email of the conversation, [inIds], is in
 * them.
 */
fun folderSheetChoices(
    inIds: Set<String>,
    folders: List<MessageFolder>,
): FolderSheetChoices {
    val listed = folders.filter { it.role != DRAFTS && !it.readLater && (it.role !in DESTINATIONS || it.id in inIds) }
    val (system, own) = listed.partition { it.role != null }
    return FolderSheetChoices(system, own)
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
fun MessageFolder.matchesSearch(query: String): Boolean = path.joinToString(" / ").containsSearch(query)

private const val DRAFTS = "drafts"
private val DESTINATIONS = setOf("junk", "trash")
