package com.otus.mail.feature.folders.domain

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
        trimmed.isEmpty() -> {
            FolderNameProblem.Empty
        }

        limit != null && trimmed.encodeToByteArray().size > limit -> {
            FolderNameProblem.TooLong
        }

        account.folders.any {
            it.parentId == parentId && it.id != renaming?.id && it.name.equals(trimmed, ignoreCase = true)
        } -> {
            FolderNameProblem.Taken
        }

        else -> {
            null
        }
    }
}

/** Whether a new folder can go under [parent], or at the top level when [parent] is null. */
fun canCreateIn(
    account: AccountFolders,
    parent: MailFolder?,
): Boolean {
    if (parent == null) return account.limits.mayCreateTopLevel
    val maxDepth = account.limits.maxDepth
    return parent.rights.mayCreateChild &&
        (maxDepth == null || ancestorCount(account.folders, parent) + 2 <= maxDepth)
}

/**
 * Where [folder] can move: the top level (null) or another folder that accepts children, never
 * into itself or anything nested below it, and within the server's depth limit.
 */
fun moveTargets(
    account: AccountFolders,
    folder: MailFolder,
): List<MailFolder?> {
    val subtree = subtreeIds(account.folders, folder)
    val height = subtreeHeight(account.folders, folder)
    val maxDepth = account.limits.maxDepth
    val folders =
        account.folderTree().map { it.folder }.filter { target ->
            target.id !in subtree &&
                target.rights.mayCreateChild &&
                (maxDepth == null || ancestorCount(account.folders, target) + 2 + height <= maxDepth)
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
