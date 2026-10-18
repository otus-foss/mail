package com.otus.mail.feature.inbox.domain

/**
 * The folders a Read view strip names. [also] when the user came from a folder, so the strip says
 * "also" before the others. No [folders] means the strip has nothing to say.
 */
data class ShownFolders(
    val also: Boolean,
    val folders: List<MessageFolder>,
)

/**
 * The folders of [mailboxIds] worth naming on the Read view: never the one the user [cameFrom],
 * Drafts, or Read later, which the bookmark shows. Ids missing from [folders] are left out. The
 * inbox comes first, then the rest by path. [cameFrom] is null without a folder context, such as
 * All Mail, search, or a notification.
 */
fun folderHint(
    mailboxIds: Set<String>,
    folders: Map<String, MessageFolder>,
    cameFrom: String?,
): ShownFolders =
    ShownFolders(
        also = cameFrom != null,
        folders =
            mailboxIds
                .filter { it != cameFrom }
                .mapNotNull(folders::get)
                .filterNot { it.role == DRAFTS_ROLE || it.readLater }
                .sortedWith(compareBy<MessageFolder> { it.role != INBOX_ROLE }.thenBy { it.sortKey }),
    )

/**
 * How a strip names these folders. One folder, or [full], shows every full path. Otherwise each
 * shows its name alone, unless another listed folder has the same name: then both show their full
 * path, as in "Finance / Invoices" beside "Freelancing / Invoices".
 */
fun List<MessageFolder>.displayPaths(full: Boolean): List<List<String>> {
    if (full || size == 1) return map { it.path }
    val clashing =
        groupingBy { it.name.lowercase() }
            .eachCount()
            .filterValues { it > 1 }
            .keys
    return map { if (it.name.lowercase() in clashing) it.path else listOf(it.name) }
}

private val MessageFolder.sortKey: String
    get() = path.joinToString("/") { it.lowercase() }

private const val DRAFTS_ROLE = "drafts"
private const val INBOX_ROLE = "inbox"
