package com.otus.mail.feature.folders.domain

/**
 * A folder is a mailbox on the server, so every mail app sees it. It is identified by the login
 * [accountId] and the server's mailbox [id], never by name: names repeat across parents.
 */
data class MailFolder(
    val accountId: String,
    val id: String,
    val name: String,
    val parentId: String? = null,
    /** The special use, such as `inbox` or `trash` (RFC 8621, section 2); null for the user's own folders. */
    val role: String? = null,
    val sortOrder: Long = 0,
    val unread: Long = 0,
    val total: Long = 0,
    val rights: FolderRights = FolderRights(),
) {
    /** Folders the user made. System folders keep their place and name. */
    val isCustom: Boolean
        get() = role == null
}

data class FolderRights(
    val mayAddItems: Boolean = true,
    val mayCreateChild: Boolean = true,
    val mayRename: Boolean = true,
    val mayDelete: Boolean = true,
    /** Messages may leave this folder, which a move requires. */
    val mayRemoveItems: Boolean = true,
    /** The read mark may change for messages in this folder. */
    val maySetSeen: Boolean = true,
    /** Other marks, such as the flag, may change for messages in this folder. */
    val maySetKeywords: Boolean = true,
)

/** What the account's server allows when creating folders (RFC 8621, section 1.3.1). */
data class FolderLimits(
    val mayCreateTopLevel: Boolean = true,
    /** One more than the most ancestors a folder may have; null when unlimited. */
    val maxDepth: Int? = null,
    /** The longest name in UTF-8 octets; null when unlimited. */
    val maxNameBytes: Int? = null,
)

/** One connected account's folders. [failure] is set when they could not be loaded. */
data class AccountFolders(
    val accountId: String,
    val accountName: String,
    val folders: List<MailFolder> = emptyList(),
    val limits: FolderLimits = FolderLimits(),
    val failure: FolderFailure? = null,
) {
    fun folder(id: String): MailFolder? = folders.firstOrNull { it.id == id }

    /** The folder new mail arrives in, located by role because servers name it differently. */
    val inbox: MailFolder?
        get() = withRole(INBOX_ROLE)

    /** The folder with a special use such as `archive` or `trash` (RFC 8621, section 2). */
    fun withRole(role: String): MailFolder? = folders.firstOrNull { it.role == role }
}

/** A folder in display order, with its nesting [depth] and its full [path] for pickers. */
data class FolderNode(
    val folder: MailFolder,
    val depth: Int,
    val path: String,
)

const val INBOX_ROLE = "inbox"
const val ARCHIVE_ROLE = "archive"
const val TRASH_ROLE = "trash"
