package com.otus.mail.feature.inbox.domain

/**
 * A mailbox pinned to the sidebar's Pinned section, below the Inbox. A pin whose folder no account
 * has, such as Read later while it is off, stays saved and shows once the folder exists.
 */
sealed interface SidebarPin {
    val storageValue: String

    /** Each account's Read later folder. */
    data object ReadLater : SidebarPin {
        override val storageValue = "read_later"
    }

    /** Each account's folder with [role], such as `sent` (RFC 8621, section 2). */
    data class Role(
        val role: String,
    ) : SidebarPin {
        override val storageValue = "$ROLE_PREFIX$role"
    }

    /** One of the user's folders, merged across accounts; [key] is its lower-case path. */
    data class Folder(
        val key: List<String>,
    ) : SidebarPin {
        override val storageValue = FOLDER_PREFIX + key.joinToString(PATH_SEPARATOR) { it.escaped() }
    }

    companion object {
        /** Read later, then the system folders in the order the sidebar always listed them. */
        val defaults: List<SidebarPin> =
            listOf(ReadLater) + listOf("drafts", "sent", "archive", "junk", "trash").map(::Role)

        /** [pins] saved as one value without commas or line breaks, so it fits one layout line. */
        fun encode(pins: List<SidebarPin>): String = pins.joinToString(SEPARATOR) { it.storageValue }

        /** Pins this version does not know are left out. */
        fun decode(saved: String): List<SidebarPin> =
            saved
                .split(SEPARATOR)
                .mapNotNull(::fromStorageValue)
                .distinct()

        /** Null for a value this version does not know. */
        fun fromStorageValue(value: String): SidebarPin? =
            when {
                value == ReadLater.storageValue -> ReadLater
                value.startsWith(ROLE_PREFIX) -> Role(value.removePrefix(ROLE_PREFIX)).takeIf { it.role.isNotEmpty() }
                value.startsWith(FOLDER_PREFIX) -> folder(value.removePrefix(FOLDER_PREFIX))
                else -> null
            }

        private fun folder(path: String): Folder? =
            path
                .split(PATH_SEPARATOR)
                .map { it.unescaped() }
                .takeIf { names -> names.none(String::isEmpty) }
                ?.let(::Folder)

        private const val SEPARATOR = ","
        private const val PATH_SEPARATOR = "/"
        private const val ROLE_PREFIX = "role:"
        private const val FOLDER_PREFIX = "folder:"
    }
}

// Folder names may hold any character, so the ones the stored value splits on are percent-encoded.
private val escapes = listOf("%" to "%25", "," to "%2C", "/" to "%2F", "\n" to "%0A", "\r" to "%0D")

private fun String.escaped(): String = escapes.fold(this) { text, (raw, code) -> text.replace(raw, code) }

private fun String.unescaped(): String =
    escapes.reversed().fold(this) { text, (raw, code) -> text.replace(code, raw, ignoreCase = true) }
