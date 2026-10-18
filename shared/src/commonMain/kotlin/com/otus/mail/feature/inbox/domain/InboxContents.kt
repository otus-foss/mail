package com.otus.mail.feature.inbox.domain

/**
 * What the Inbox page lists besides the inbox itself: Archive, the user's own folders, Spam, and
 * Trash. With [ownFolders] on, mail the user filed away stays on the page. Mail stays in its own
 * folders; this only chooses what the page shows. Whose mail it shows is the shared "Show mail
 * from" choice.
 */
data class InboxContents(
    val spam: Boolean = false,
    val trash: Boolean = false,
    val archive: Boolean = false,
    val ownFolders: Boolean = false,
) {
    /** The folder roles the page loads (RFC 8621, section 2), the inbox first. */
    val roles: List<String>
        get() =
            buildList {
                add(INBOX_ROLE)
                if (archive) add(ARCHIVE_ROLE)
                if (spam) add(JUNK_ROLE)
                if (trash) add(TRASH_ROLE)
            }

    companion object {
        /** One `name=value` line per setting; unknown lines, such as older address choices, are ignored. */
        fun encode(contents: InboxContents): String =
            listOf(
                SPAM to contents.spam,
                TRASH to contents.trash,
                ARCHIVE to contents.archive,
                OWN_FOLDERS to contents.ownFolders,
            ).joinToString("\n") { (name, on) -> "$name=$on" }

        /** Missing or unreadable values read as the defaults. */
        fun decode(saved: String): InboxContents {
            val lines = saved.lines().map { it.substringBefore("=") to it.substringAfter("=", "") }

            fun flag(name: String) = lines.lastOrNull { it.first == name }?.second?.toBooleanStrictOrNull() ?: false
            return InboxContents(
                spam = flag(SPAM),
                trash = flag(TRASH),
                archive = flag(ARCHIVE),
                ownFolders = flag(OWN_FOLDERS),
            )
        }

        private const val SPAM = "spam"
        private const val TRASH = "trash"
        private const val ARCHIVE = "archive"
        private const val OWN_FOLDERS = "ownFolders"
        private const val ARCHIVE_ROLE = "archive"
        private const val INBOX_ROLE = "inbox"
        private const val JUNK_ROLE = "junk"
        private const val TRASH_ROLE = "trash"
    }
}
