package com.otus.mail.feature.inbox.domain

import com.otus.mail.core.mailbox.MailboxRole

/**
 * A pill in the inbox row. A [Filter] narrows the mail shown; a [FolderPill] shows a folder's mail
 * in its place. Folder pills find their folders by role or server id, so renaming one keeps them.
 */
sealed interface InboxPill {
    val storageValue: String

    enum class Filter(
        override val storageValue: String,
    ) : InboxPill {
        Unread("unread"),
        Attachments("attachments"),
        Flagged("flagged"),

        /** Conversations: threads where the user and someone else both wrote. */
        Conversations("conversations"),
    }

    sealed interface FolderPill : InboxPill

    /** Each account's Read later folder. */
    data object ReadLater : FolderPill {
        override val storageValue = "read_later"
    }

    /** Each account's folder with [role], such as `archive` (RFC 8621, section 2). */
    data class Role(
        val role: MailboxRole,
    ) : FolderPill {
        override val storageValue = "$ROLE_PREFIX${role.wire}"
    }

    /** One of the user's own folders: [accountId] is the login and [folderId] the server's mailbox id. */
    data class Folder(
        val accountId: String,
        val folderId: String,
    ) : FolderPill {
        override val storageValue = "$FOLDER_PREFIX$accountId/$folderId"
    }

    companion object {
        private const val ROLE_PREFIX = "role:"
        private const val FOLDER_PREFIX = "folder:"

        /** The row a new install shows; turning Read later on adds its pill. */
        fun defaults(readLater: Boolean): List<InboxPill> =
            listOfNotNull(Filter.Unread, Filter.Attachments, ReadLater.takeIf { readLater })

        /** [pills] saved as one value, one pill per line. */
        fun encode(pills: List<InboxPill>): String = pills.joinToString("\n") { it.storageValue }

        /** Lines this version does not know are left out. */
        fun decode(saved: String): List<InboxPill> = saved.lines().mapNotNull(::fromStorageValue).distinct()

        /** Null for a value this version does not know. Server ids never contain "/" (RFC 8620, section 1.2). */
        fun fromStorageValue(value: String): InboxPill? =
            when {
                value == ReadLater.storageValue -> ReadLater
                value.startsWith(ROLE_PREFIX) -> MailboxRole.of(value.removePrefix(ROLE_PREFIX))?.let(::Role)
                value.startsWith(FOLDER_PREFIX) -> folderFrom(value)
                else -> Filter.entries.firstOrNull { it.storageValue == value }
            }

        private fun folderFrom(value: String): Folder? {
            val ids = value.removePrefix(FOLDER_PREFIX)
            return Folder(ids.substringBeforeLast("/"), ids.substringAfterLast("/"))
                .takeIf { "/" in ids && it.accountId.isNotEmpty() && it.folderId.isNotEmpty() }
        }
    }
}
