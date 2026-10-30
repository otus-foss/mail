package com.otus.mail.feature.inbox.presentation

import com.otus.designsystem.shared.filters.components.FolderCheck
import com.otus.mail.feature.inbox.domain.FolderRef
import com.otus.mail.feature.inbox.domain.InboxFilters

/** [folder]'s checkbox: its own choice, or included by a parent chosen with its subfolders. */
fun InboxFilters.check(
    folder: FilterFolder,
    options: FilterOptions,
): FolderCheck =
    when {
        folders.any { it.key == folder.key } -> FolderCheck.On
        folders.any { it.includes(folder, options) } -> FolderCheck.Included
        else -> FolderCheck.Off
    }

private fun FolderRef.includes(
    folder: FilterFolder,
    options: FilterOptions,
): Boolean =
    includeSubfolders && loginId == folder.key.loginId &&
        options.folder(key)?.descendants?.contains(folder.key.mailboxId) == true

/**
 * These filters with [folder] turned off, or on with its subfolders when it has any. Folders it
 * then includes leave the selection, because the parent covers them.
 */
fun InboxFilters.toggle(folder: FilterFolder): InboxFilters {
    val chosen = folders.firstOrNull { it.key == folder.key }
    val ref = FolderRef(folder.key.loginId, folder.key.mailboxId, includeSubfolders = folder.hasChildren)
    if (chosen != null) return withFolder(chosen, on = false)
    val covered = folders.filter { it.loginId == folder.key.loginId && it.mailboxId in folder.descendants }
    return covered.fold(this) { filters, child -> filters.withFolder(child, on = false) }.withFolder(ref, on = true)
}

/** The chosen [folder] with or without its subfolders. */
fun InboxFilters.includeSubfolders(
    folder: FilterFolder,
    include: Boolean,
): InboxFilters =
    folders
        .firstOrNull { it.key == folder.key }
        ?.let { withFolder(it.copy(includeSubfolders = include), on = true) }
        ?: this

/**
 * The folders the tree shows: those matching [query] by name while searching, else every folder
 * whose parents are all open, none of them in [collapsed].
 */
fun FilterAccount.shownFolders(
    query: String,
    collapsed: Set<FolderKey>,
): List<FilterFolder> {
    val wanted = query.trim()
    if (wanted.isNotEmpty()) return folders.filter { it.name.contains(wanted, ignoreCase = true) }
    val byId = folders.associateBy { it.key.mailboxId }
    return folders.filter { folder ->
        generateSequence(folder.parentId?.let(byId::get)) { it.parentId?.let(byId::get) }
            .take(folder.depth)
            .none { it.key in collapsed }
    }
}
