package com.otusfoss.mail.core.messages.domain

import com.otusfoss.mail.core.mailbox.MailboxRole

/**
 * The folders a Read view strip names. [also] when the user came from a folder, so the strip says
 * "also" before the others. No [folders] means the strip has nothing to say.
 */
data class ShownFolders(
    val also: Boolean,
    val folders: List<MessageFolder>,
)

/**
 * The folders of [mailboxIds] worth naming on a Read view strip: never the ones the list the user
 * [cameFrom] shows, Drafts, Sent, Trash, Spam, or Read later, which the bookmark shows. [cameFrom]
 * is empty without a folder context, such as All Mail, search, or a notification.
 */
fun folderHint(
    mailboxIds: Set<String>,
    folders: Map<String, MessageFolder>,
    cameFrom: Set<String>,
): ShownFolders =
    ShownFolders(
        also = cameFrom.isNotEmpty(),
        folders =
            orderedFolders(mailboxIds - cameFrom, folders)
                .filterNot { it.role in UNHINTED || it.readLater },
    )

/** Every folder of [mailboxIds] that [folders] knows, the inbox first and then the rest by path. */
fun orderedFolders(
    mailboxIds: Set<String>,
    folders: Map<String, MessageFolder>,
): List<MessageFolder> =
    mailboxIds
        .mapNotNull(folders::get)
        .sortedWith(compareBy<MessageFolder> { it.role != MailboxRole.Inbox }.thenBy { it.sortKey })

/** Whether one of [mailboxIds] is the Spam folder, where remote images and unsubscribing stay off. */
fun inJunk(
    mailboxIds: Set<String>,
    folders: Map<String, MessageFolder>,
): Boolean = mailboxIds.any { folders[it]?.role == MailboxRole.Junk }

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

/** Folders the strips never name: where drafts, sent, and deleted mail and spam live. */
private val UNHINTED = setOf(MailboxRole.Drafts, MailboxRole.Sent, MailboxRole.Trash, MailboxRole.Junk)
