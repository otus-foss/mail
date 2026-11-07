package com.otus.mail.feature.folders.domain

import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.folderTree

/**
 * Where a dragged [folder] lands: under [parentId], or the top level when it is null, with the new
 * `sortOrder` (RFC 8621, section 2) of every folder whose number changes. One `Mailbox/set` saves it.
 */
data class FolderPlacement(
    val folder: MailFolder,
    val parentId: String?,
    val sortOrders: Map<String, Long>,
) {
    val changesParent: Boolean
        get() = parentId != folder.parentId
}

/** Whether the user can drag [folder]: their own folders, never Read later, which keeps its place. */
fun AccountFolders.canPlace(folder: MailFolder): Boolean =
    folder.isCustom && folder.id != readLaterId && folder.rights.mayRename

/** The folders a drag places among under [parentId], in display order. */
fun AccountFolders.placeableChildren(parentId: String?): List<MailFolder> =
    folderTree().map { it.folder }.filter { it.parentId == parentId && canPlace(it) }

/**
 * Puts [folder] under [parentId] at [index] among that parent's [placeableChildren] without it,
 * last by default. Null when nothing changes or the server would refuse: a system folder, missing
 * rights, too deep, into itself, or a sibling with the same name. Only the moved folder gets a new
 * number while one fits between its neighbors; otherwise the siblings are numbered again.
 */
fun AccountFolders.placement(
    folder: MailFolder,
    parentId: String?,
    index: Int = Int.MAX_VALUE,
): FolderPlacement? {
    val nests = parentId != folder.parentId
    val siblings = placeableChildren(parentId).filter { it.id != folder.id }
    val at = index.coerceIn(0, siblings.size)
    val order = siblings.take(at) + folder + siblings.drop(at)
    val changes = if (nests) canNest(folder, parentId) else order != placeableChildren(parentId)
    return FolderPlacement(folder, parentId, sortOrders(folder, siblings, at, order))
        .takeIf { canPlace(folder) && changes }
}

/** The numbers that change when [folder] goes to [at] among [siblings], making [order]. */
private fun sortOrders(
    folder: MailFolder,
    siblings: List<MailFolder>,
    at: Int,
    order: List<MailFolder>,
): Map<String, Long> =
    when (val fitted = fittedOrder(folder.sortOrder, siblings.getOrNull(at - 1), siblings.getOrNull(at))) {
        null -> renumbered(order)
        folder.sortOrder -> emptyMap()
        else -> mapOf(folder.id to fitted)
    }

/** The account with [placement] applied, so the list shows it before the server answers. */
fun AccountFolders.placed(placement: FolderPlacement): AccountFolders {
    fun List<MailFolder>.placed() =
        map { folder ->
            val order = placement.sortOrders[folder.id] ?: folder.sortOrder
            when {
                folder.id == placement.folder.id -> folder.copy(parentId = placement.parentId, sortOrder = order)
                else -> folder.copy(sortOrder = order)
            }
        }
    val sharedIn = placement.folder.sharedIn ?: return copy(folders = folders.placed())
    return copy(
        shared = shared.map { if (it.jmapAccountId == sharedIn) it.copy(folders = it.folders.placed()) else it },
    )
}

/** The folders [folder] sits among: the login's own, or the shared account it comes from. */
fun AccountFolders.scopeOf(folder: MailFolder): AccountFolders? =
    folder.sharedIn?.let { id -> shared.firstOrNull { it.jmapAccountId == id }?.asAccount(accountId) } ?: this

private fun AccountFolders.canNest(
    folder: MailFolder,
    parentId: String?,
): Boolean =
    moveTargets(this, folder).any { it?.id == parentId } &&
        folderNameProblem(folder.name, parentId, this, renaming = folder) != FolderNameProblem.Taken

/**
 * A number strictly between [before]'s and [after]'s, keeping [current] when it already fits, so
 * the name never decides the order; null when no whole number fits.
 */
private fun fittedOrder(
    current: Long,
    before: MailFolder?,
    after: MailFolder?,
): Long? {
    val low = before?.sortOrder ?: -1
    val high = after?.sortOrder ?: (MAX_SORT_ORDER + 1)
    return when {
        current in (low + 1) until high -> current
        high - low < 2 -> null
        before == null -> (high - SORT_STEP).coerceAtLeast(high / 2)
        after == null -> (low + SORT_STEP).coerceAtMost(low + (high - low) / 2)
        else -> low + (high - low) / 2
    }
}

/** Numbers [order] in steps, sending only the numbers that change. */
private fun renumbered(order: List<MailFolder>): Map<String, Long> =
    order
        .mapIndexed { index, folder -> folder to (index + 1) * SORT_STEP }
        .filter { (folder, number) -> folder.sortOrder != number }
        .associate { (folder, number) -> folder.id to number }

private const val SORT_STEP = 10L

/** `sortOrder` is an UnsignedInt (RFC 8621, section 2), at most 2^32 - 1. */
private const val MAX_SORT_ORDER = 4_294_967_295L
