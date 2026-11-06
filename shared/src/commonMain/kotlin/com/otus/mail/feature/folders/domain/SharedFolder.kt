package com.otus.mail.feature.folders.domain

import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.folderTree

/**
 * The user's own folders of every account, merged by path. Folders whose names match from the top
 * level down, ignoring case, are one shared folder, so Clients / Acme in two accounts is one row
 * holding both accounts' mail. [path] names it from the top level down as the first account spells
 * it, and [key] is that path in lower case. [folders] are the accounts' folders at it; a folder
 * with only nested folders in some account holds none of that account's.
 */
data class SharedFolder(
    val name: String,
    val path: List<String>,
    val folders: List<MailFolder>,
    val children: List<SharedFolder> = emptyList(),
) {
    val key: List<String>
        get() = path.map { it.lowercase() }

    /** This folder, then its nested folders, in order. */
    fun flatten(): List<SharedFolder> = listOf(this) + children.flatMap { it.flatten() }
}

/**
 * Every account's own folders, merged by path in the order the accounts list them. System
 * folders and Read later are left out and their nested folders move up a level, except under
 * Trash and Junk, where the folders are on their way out.
 */
fun sharedFolders(accounts: List<AccountFolders>): List<SharedFolder> =
    merge(accounts.flatMap { it.ownFolderPaths() }, 0)

/** The folder at [key] among [folders] and their nested folders. */
fun sharedFolder(
    folders: List<SharedFolder>,
    key: List<String>,
): SharedFolder? = folders.flatMap { it.flatten() }.firstOrNull { it.key == key }

private class FolderPath(
    val names: List<String>,
    val folder: MailFolder,
)

private fun AccountFolders.ownFolderPaths(): List<FolderPath> {
    val kept = folders.filterNot(::inBin)
    val nodes = folderTree(kept, readLaterId) { !isOwnFolder(it) }
    // Parents come first, so a folder's path is the open path cut to its depth, plus its name.
    val open = ArrayList<String>()
    return nodes.map { node ->
        while (open.size > node.depth) open.removeAt(open.lastIndex)
        open += node.folder.name
        FolderPath(open.toList(), node.folder)
    }
}

private fun merge(
    paths: List<FolderPath>,
    depth: Int,
): List<SharedFolder> =
    paths.groupBy { it.names[depth].lowercase() }.values.map { group ->
        val (here, below) = group.partition { it.names.size == depth + 1 }
        SharedFolder(
            name = group.first().names[depth],
            path = group.first().names.take(depth + 1),
            folders = here.map { it.folder },
            children = merge(below, depth + 1),
        )
    }
