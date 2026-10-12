package com.otus.mail.feature.inbox.presentation

/** The system folders among these targets, in the order the move sheet shows them. */
fun List<MoveTarget>.shortcuts(): List<MoveTarget> =
    FolderShortcut.entries.mapNotNull { shortcut -> firstOrNull { it.shortcut == shortcut } }

/** The user's own folders among these targets, in tree order. */
fun List<MoveTarget>.ownFolders(): List<MoveTarget> = filter { it.own }

/** Whether the target's name, path, or system folder label contains [query]. */
fun MoveTarget.matchesSearch(query: String): Boolean =
    listOfNotNull(name, label, shortcut?.label).any { it.containsSearch(query) }

/** Whether this text contains [query], ignoring case and the spaces around it; a blank query matches. */
fun String.containsSearch(query: String): Boolean = contains(query.trim(), ignoreCase = true)

/** The ids of folders here that have nested folders right after them, which the sheet can collapse. */
fun List<MoveTarget>.parentIds(): Set<String> =
    zipWithNext().filter { (folder, next) -> next.depth > folder.depth }.map { it.first.folderId }.toSet()

/**
 * The ids of folders hidden because a folder above them is [collapsed]. The list must be in tree
 * order, where a folder's nested folders follow it with a greater depth.
 */
fun List<MoveTarget>.collapsedAway(collapsed: Set<String>): Set<String> =
    buildSet {
        // The depth of the collapsed folder whose subtree is being skipped, if any.
        var hidingBelow: Int? = null
        this@collapsedAway.forEach { folder ->
            val hidden = hidingBelow?.let { folder.depth > it } == true
            if (hidden) {
                add(folder.folderId)
            } else {
                hidingBelow = folder.depth.takeIf { folder.folderId in collapsed }
            }
        }
    }
