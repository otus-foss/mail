package com.otus.mail.core.mailbox

/** The account's folders in display order, with its Read later folder right after the inbox. */
fun AccountFolders.folderTree(hidden: (MailFolder) -> Boolean = { false }): List<FolderNode> =
    folderTree(folders, readLaterId, hidden)

/** The Read later folder with its subfolders nested under it, wherever it sits; empty without one. */
fun AccountFolders.readLaterTree(): List<FolderNode> {
    val folder = readLater ?: return emptyList()
    val subtree = subtreeIds(folders, folder)
    // Its parent is left out, so Read later becomes the root.
    return folderTree(folders.filter { it.id in subtree }, readLaterId)
}

/**
 * Parents come before their children. Siblings sort system folders first, in a fixed order, then
 * by the server's sort order and by name. The [readLaterId] folder sorts right after the inbox. A
 * folder whose parent is missing becomes a root, and a parent cycle cannot repeat folders.
 * [hidden] folders are left out; their children take their place.
 */
fun folderTree(
    folders: List<MailFolder>,
    readLaterId: String? = null,
    hidden: (MailFolder) -> Boolean = { false },
): List<FolderNode> {
    val ids = folders.map { it.id }.toSet()
    val children = folders.groupBy { folder -> folder.parentId?.takeIf { it in ids && it != folder.id } }
    val order = siblingOrder(readLaterId)
    val visited = mutableSetOf<String>()
    val result = mutableListOf<FolderNode>()

    fun visit(
        parent: String?,
        depth: Int,
        above: List<String>,
    ) {
        children[parent].orEmpty().sortedWith(order).forEach { folder ->
            if (!visited.add(folder.id)) return@forEach
            if (hidden(folder)) {
                visit(folder.id, depth, above)
            } else {
                val path = above + folder.mailboxName
                result += FolderNode(folder, depth, path, readLater = folder.id == readLaterId)
                visit(folder.id, depth + 1, path)
            }
        }
    }
    visit(null, 0, emptyList())
    // Folders caught in a parent cycle never reach a root; show them at the top level.
    folders.filter { it.id !in visited }.sortedWith(order).forEach { folder ->
        visited += folder.id
        result += FolderNode(folder, 0, listOf(folder.mailboxName), readLater = folder.id == readLaterId)
    }
    return result
}

/** [folder] and everything nested below it. */
fun subtreeIds(
    folders: List<MailFolder>,
    folder: MailFolder,
): Set<String> {
    val ids = mutableSetOf(folder.id)
    var grew = true
    while (grew) {
        val more = folders.filter { it.parentId in ids && it.id !in ids }
        ids += more.map { it.id }
        grew = more.isNotEmpty()
    }
    return ids
}

/** The folders above [folder], nearest first. A parent cycle ends the list. */
fun ancestors(
    folders: List<MailFolder>,
    folder: MailFolder,
): List<MailFolder> {
    val byId = folders.associateBy { it.id }
    val seen = mutableSetOf(folder.id)
    return buildList {
        var parent = folder.parentId?.let(byId::get)
        while (parent != null && seen.add(parent.id)) {
            add(parent)
            parent = parent.parentId?.let(byId::get)
        }
    }
}

fun ancestorCount(
    folders: List<MailFolder>,
    folder: MailFolder,
): Int = ancestors(folders, folder).size

private fun siblingOrder(readLaterId: String?): Comparator<MailFolder> =
    compareBy({ it.rank(readLaterId) }, { it.sortOrder }, { it.name.lowercase() })

/**
 * System folders in sidebar order with Read later right after the inbox, then other special uses,
 * then the user's own folders.
 */
private fun MailFolder.rank(readLaterId: String?): Int {
    val slots = MailboxRole.entries.size + 1
    return when {
        role == MailboxRole.Inbox -> 0
        id == readLaterId -> 1
        role != null -> role.ordinal + 1
        otherRole != null -> slots
        else -> slots + 1
    }
}
