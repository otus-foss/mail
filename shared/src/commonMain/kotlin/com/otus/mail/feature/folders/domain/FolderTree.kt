package com.otus.mail.feature.folders.domain

/** The account's folders in display order, with its Read later folder right after the inbox. */
fun AccountFolders.folderTree(hidden: (MailFolder) -> Boolean = { false }): List<FolderNode> =
    folderTree(folders, readLaterId, hidden)

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
        prefix: String,
    ) {
        children[parent].orEmpty().sortedWith(order).forEach { folder ->
            if (!visited.add(folder.id)) return@forEach
            if (hidden(folder)) {
                visit(folder.id, depth, prefix)
            } else {
                val path = prefix + folder.name
                result += FolderNode(folder, depth, path, readLater = folder.id == readLaterId)
                visit(folder.id, depth + 1, "$path / ")
            }
        }
    }
    visit(null, 0, "")
    // Folders caught in a parent cycle never reach a root; show them at the top level.
    folders.filter { it.id !in visited }.sortedWith(order).forEach { folder ->
        visited += folder.id
        result += FolderNode(folder, 0, folder.name, readLater = folder.id == readLaterId)
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

/** How many ancestors [folder] has. */
fun ancestorCount(
    folders: List<MailFolder>,
    folder: MailFolder,
): Int {
    val byId = folders.associateBy { it.id }
    val seen = mutableSetOf(folder.id)
    var parent = folder.parentId?.let(byId::get)
    while (parent != null && seen.add(parent.id)) parent = parent.parentId?.let(byId::get)
    return seen.size - 1
}

// Roles never contain spaces, so this slot cannot match a server's role.
private const val READ_LATER_SLOT = "read later"

private val ROLE_ORDER = listOf(INBOX_ROLE, READ_LATER_SLOT, "drafts", "sent", ARCHIVE_ROLE, "junk", TRASH_ROLE)

private fun siblingOrder(readLaterId: String?): Comparator<MailFolder> =
    compareBy({ it.rank(readLaterId) }, { it.sortOrder }, { it.name.lowercase() })

/** System folders and Read later in their usual order, then other special uses, then the user's own folders. */
private fun MailFolder.rank(readLaterId: String?): Int {
    val slot = (if (id == readLaterId) READ_LATER_SLOT else role) ?: return ROLE_ORDER.size + 1
    return ROLE_ORDER.indexOf(slot).takeIf { it >= 0 } ?: ROLE_ORDER.size
}
