package com.otus.mail.feature.inbox.domain

/**
 * What the Inbox page lists besides the inbox itself: Spam and Trash. Mail stays in its own
 * folders; this only chooses what the page shows. Whose mail it shows is the shared "Show mail
 * from" choice.
 */
data class InboxContents(
    val spam: Boolean = false,
    val trash: Boolean = false,
) {
    /** The folder roles the page loads (RFC 8621, section 2), the inbox first. */
    val roles: List<String>
        get() =
            buildList {
                add(INBOX_ROLE)
                if (spam) add(JUNK_ROLE)
                if (trash) add(TRASH_ROLE)
            }

    companion object {
        /** One `name=value` line per setting; unknown lines, such as older address choices, are ignored. */
        fun encode(contents: InboxContents): String = "$SPAM=${contents.spam}\n$TRASH=${contents.trash}"

        /** Missing or unreadable values read as the defaults. */
        fun decode(saved: String): InboxContents {
            val lines = saved.lines().map { it.substringBefore("=") to it.substringAfter("=", "") }

            fun flag(name: String) = lines.lastOrNull { it.first == name }?.second?.toBooleanStrictOrNull() ?: false
            return InboxContents(spam = flag(SPAM), trash = flag(TRASH))
        }

        private const val SPAM = "spam"
        private const val TRASH = "trash"
        private const val INBOX_ROLE = "inbox"
        private const val JUNK_ROLE = "junk"
        private const val TRASH_ROLE = "trash"
    }
}
