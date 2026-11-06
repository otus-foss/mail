package com.otus.mail.core.settings.domain

import com.otus.mail.core.mailbox.MailboxRole

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
    /** The folder roles the page loads, the inbox first. */
    val roles: List<MailboxRole>
        get() =
            buildList {
                add(MailboxRole.Inbox)
                if (archive) add(MailboxRole.Archive)
                if (spam) add(MailboxRole.Junk)
                if (trash) add(MailboxRole.Trash)
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
    }
}
