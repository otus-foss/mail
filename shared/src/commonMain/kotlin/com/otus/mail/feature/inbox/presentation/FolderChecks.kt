package com.otus.mail.feature.inbox.presentation

import com.otus.designsystem.components.FolderCheck
import com.otus.mail.feature.inbox.domain.FolderRef
import com.otus.mail.feature.inbox.domain.InboxFilters

/** [folder]'s checkbox: its own choice, or included by a parent chosen with its subfolders. */
fun InboxFilters.check(
    folder: FilterFolder,
    options: FilterOptions,
): FolderCheck =
    when {
        folders.any { it.key == folder.key } -> {
            FolderCheck.On
        }

        folders.any { ref ->
            ref.includeSubfolders &&
                ref.loginId == folder.key.loginId &&
                options.folder(ref.key)?.descendants?.contains(folder.key.mailboxId) == true
        } -> {
            FolderCheck.Included
        }

        else -> {
            FolderCheck.Off
        }
    }

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
): InboxFilters {
    val chosen = folders.firstOrNull { it.key == folder.key } ?: return this
    return withFolder(chosen.copy(includeSubfolders = include), on = true)
}

/** The selected folders' chips: shortcuts first, up to [MAX_SHORTCUTS], then other selected folders. */
fun FilterOptions.folderChips(filters: InboxFilters): List<FilterFolder> {
    val shortcuts = shortcuts.take(MAX_SHORTCUTS)
    val selected = filters.folders.map { it.key }.filter { it !in shortcuts }
    return (shortcuts + selected).mapNotNull(::folder)
}

/** How many folders the chip for [ref] stands for beyond its own: its subfolders while they are included. */
fun FilterOptions.includedCount(ref: FolderRef): Int =
    if (ref.includeSubfolders) folder(ref.key)?.descendants?.size ?: 0 else 0

const val MAX_SHORTCUTS = 6

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
