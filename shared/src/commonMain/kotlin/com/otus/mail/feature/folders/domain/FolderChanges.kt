package com.otus.mail.feature.folders.domain

import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.FolderLimits
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.ancestorCount
import com.otus.mail.core.mailbox.folderTree
import com.otus.mail.core.mailbox.subtreeIds

/**
 * Checks a new [name] for a folder under [parentId] before anything is sent. Names are trimmed, as
 * servers do, and sibling names are compared without case, as Stalwart does, so they stay distinct
 * in every client. [renaming] is the folder being renamed; its own name is not a conflict.
 */
fun folderNameProblem(
    name: String,
    parentId: String?,
    account: AccountFolders,
    renaming: MailFolder? = null,
): FolderNameProblem? {
    val trimmed = name.trim()
    val limit = account.limits.maxNameBytes
    return when {
        trimmed.isEmpty() -> FolderNameProblem.Empty
        limit != null && trimmed.encodeToByteArray().size > limit -> FolderNameProblem.TooLong
        account.nameTaken(trimmed, parentId, renaming) -> FolderNameProblem.Taken
        else -> null
    }
}

private fun AccountFolders.nameTaken(
    name: String,
    parentId: String?,
    renaming: MailFolder?,
): Boolean = folders.any { it.parentId == parentId && it.id != renaming?.id && it.name.equals(name, ignoreCase = true) }

/** Whether a new folder can go under [parent], or at the top level when [parent] is null. */
fun canCreateIn(
    account: AccountFolders,
    parent: MailFolder?,
): Boolean {
    if (parent == null) return account.limits.mayCreateTopLevel
    return parent.rights.mayCreateChild && ancestorCount(account.folders, parent) + 2 <= account.limits.levels
}

/**
 * Where [folder] can move: the top level (null) or another folder that accepts children, never
 * into itself or anything nested below it, and within [FolderLimits.levels].
 */
fun moveTargets(
    account: AccountFolders,
    folder: MailFolder,
): List<MailFolder?> {
    val subtree = subtreeIds(account.folders, folder)
    val height = subtreeHeight(account.folders, folder)
    val levels = account.limits.levels
    val folders =
        account.folderTree().map { it.folder }.filter { target ->
            target.id !in subtree &&
                target.rights.mayCreateChild &&
                ancestorCount(account.folders, target) + 2 + height <= levels
        }
    val topLevel: List<MailFolder?> = if (account.limits.mayCreateTopLevel) listOf(null) else emptyList()
    return topLevel + folders
}

/** Levels below [folder]: 0 when it has no subfolders. */
private fun subtreeHeight(
    folders: List<MailFolder>,
    folder: MailFolder,
): Int {
    val depth = ancestorCount(folders, folder)
    return subtreeIds(folders, folder).maxOf { id ->
        folders.firstOrNull { it.id == id }?.let { ancestorCount(folders, it) - depth } ?: 0
    }
}
