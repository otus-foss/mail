package com.otusfoss.mail.core.mailbox

import com.otusfoss.mail.core.mail.MailFailure

/**
 * A folder is a mailbox on the server, so every mail app sees it. It is identified by the login
 * [accountId] and the server's mailbox [id], never by name: names repeat across parents.
 */
data class MailFolder(
    val accountId: String,
    val id: String,
    val name: String,
    val parentId: String? = null,
    /** The special use Mail gives a place of its own; null for the user's own folders and other special uses. */
    val role: MailboxRole? = null,
    val sortOrder: Long = 0,
    val unread: Long = 0,
    val total: Long = 0,
    val rights: FolderRights = FolderRights(),
    /** The JMAP account another user shares this mailbox from; null for the login's own mailboxes. */
    val sharedIn: String? = null,
    /** A role Mail has no place of its own for, such as `important`, in lower case; null otherwise. */
    val otherRole: String? = null,
) {
    /** Folders the user made. System folders keep their place and name. */
    val isCustom: Boolean
        get() = role == null && otherRole == null

    /** Whether it is Trash or Spam. */
    val isBin: Boolean
        get() = role?.isBin == true

    /**
     * The name messages and search file it under: the app's for the inbox, which servers translate,
     * else the server's.
     */
    val mailboxName: String
        get() = if (role == MailboxRole.Inbox) role.title else name
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

/** What the account's server allows for folders (RFC 8621, section 1.3.1). */
data class FolderLimits(
    val mayCreateTopLevel: Boolean = true,
    /** One more than the most ancestors a folder may have; null when unlimited. */
    val maxDepth: Int? = null,
    /** The longest name in UTF-8 octets; null when unlimited. */
    val maxNameBytes: Int? = null,
    /** The most folders one message may be in at once; null when unlimited. */
    val maxFoldersPerMessage: Int? = null,
) {
    /** The most levels a folder Otus creates or moves may sit at: the server's limit, at most [MAX_FOLDER_LEVELS]. */
    val levels: Int
        get() = minOf(maxDepth ?: MAX_FOLDER_LEVELS, MAX_FOLDER_LEVELS)
}

/**
 * Otus nests folders at most this deep, a top-level folder being level 1, so names keep room
 * beside their indent. Folders other apps nest deeper still show.
 */
const val MAX_FOLDER_LEVELS = 5

/** One connected account's folders. [failure] is set when they could not be loaded. */
data class AccountFolders(
    val accountId: String,
    val accountName: String,
    val folders: List<MailFolder> = emptyList(),
    val limits: FolderLimits = FolderLimits(),
    val failure: FolderFailure? = null,
    /** The folder Read later files mail into; null while that setting is off or it has none. */
    val readLaterId: String? = null,
    /** The name the server gives this login's own mail account, which other logins see as the owner. */
    val mailboxOwner: String = "",
    /** Mailboxes other users share with this login. They stay out of [folders], which the sidebar lists. */
    val shared: List<SharedMailboxes> = emptyList(),
) {
    fun folder(id: String): MailFolder? = folders.firstOrNull { it.id == id }

    val readLater: MailFolder?
        get() = readLaterId?.let(::folder)

    /** The folder new mail arrives in, located by role because servers name it differently. */
    val inbox: MailFolder?
        get() = folders.firstOrNull { it.role == MailboxRole.Inbox }

    /** The user's own folders, leaving out system folders and Read later, which have places of their own. */
    fun isOwnFolder(folder: MailFolder): Boolean = folder.isCustom && folder.id != readLaterId

    /** Whether [folder] is Trash or Spam or sits under one, where mail is on its way out. */
    fun inBin(folder: MailFolder): Boolean = (listOf(folder) + ancestors(folders, folder)).any { it.isBin }
}

/**
 * A folder in display order, with its nesting [depth] and the names of its [path] from the top
 * level down, its own last, each a [MailFolder.mailboxName]. [readLater] marks the account's Read
 * later folder.
 */
data class FolderNode(
    val folder: MailFolder,
    val depth: Int,
    val path: List<String>,
    val readLater: Boolean = false,
)

/** Why an account's folders, or a change to one, failed. */
sealed interface FolderFailure {
    data object AlreadyExists : FolderFailure

    data object Invalid : FolderFailure

    data object HasSubfolders : FolderFailure

    data object HasMail : FolderFailure

    data object NotFound : FolderFailure

    data class Mail(
        val failure: MailFailure,
    ) : FolderFailure
}

/**
 * The mailboxes another user shares with a login: one session account with `isPersonal: false`
 * (RFC 8620, section 2) that has mail. [owner] is that account's name, such as the owner's address.
 * Each folder's [MailFolder.sharedIn] is [jmapAccountId], and its `myRights` say what the login may
 * do. These are JMAP shared mailboxes, unlike a shared folder, which merges a user's own folders by
 * path across logins.
 */
data class SharedMailboxes(
    val jmapAccountId: String,
    val owner: String,
    val folders: List<MailFolder>,
    val limits: FolderLimits = FolderLimits(),
) {
    /** The group as an account, so the folder checks and the tree work on it unchanged. */
    fun asAccount(loginId: String): AccountFolders = AccountFolders(loginId, owner, folders, limits)
}

/** How a folder path reads on screen, such as "Clients / Acme". */
fun List<String>.pathText(): String = joinToString(" / ")
