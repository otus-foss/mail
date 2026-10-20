package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.MessageFolder
import com.otus.mail.feature.inbox.domain.orderedFolders

/** The Folders sheet's two lists: the folders a message is [current]ly in, then [others] it can join. */
data class FolderSheetLists(
    val current: List<MessageFolder>,
    val others: List<MessageFolder>,
)

/**
 * The folders of [folders] that any email of a conversation is in, [inIds], inbox first and then
 * by path, and the rest in the tree order of [folders], so they can show as a tree. Drafts and
 * Read later stay out, since the bookmark shows Read later. Spam and Trash are places mail moves
 * to, not folders it is added to, so they are listed only when the mail is in them.
 */
fun folderSheetLists(
    inIds: Set<String>,
    folders: List<MessageFolder>,
): FolderSheetLists {
    val listed = { folder: MessageFolder -> folder.role != DRAFTS && !folder.readLater }
    return FolderSheetLists(
        current = orderedFolders(inIds, folders.associateBy { it.id }).filter(listed),
        others = folders.filter { it.id !in inIds && listed(it) && it.role !in DESTINATIONS },
    )
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
