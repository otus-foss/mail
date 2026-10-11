package com.otus.mail.feature.folders.domain

/**
 * A system folder the sidebar can show once for every account, in sidebar order. [title] replaces
 * each server's own name, which differs between servers, such as Stalwart's "Junk Mail" for [Spam].
 */
enum class CombinedFolder(
    val role: String,
    val title: String,
) {
    Drafts(DRAFTS_ROLE, "Drafts"),
    Sent(SENT_ROLE, "Sent"),
    Archive(ARCHIVE_ROLE, "Archive"),
    Spam(JUNK_ROLE, "Spam"),
    Trash(TRASH_ROLE, "Trash"),
    ;

    companion object {
        val roles: Set<String> = entries.map { it.role }.toSet()
    }
}

/** Each combined folder that some account has, with the unread mail of all of them. */
fun combinedFolders(accounts: List<AccountFolders>): Map<CombinedFolder, Long> {
    val folders = accounts.flatMap { it.folders }
    return CombinedFolder.entries
        .associateWith { combined -> folders.filter { it.role == combined.role } }
        .filterValues { it.isNotEmpty() }
        .mapValues { (_, matching) -> matching.sumOf { it.unread } }
}
