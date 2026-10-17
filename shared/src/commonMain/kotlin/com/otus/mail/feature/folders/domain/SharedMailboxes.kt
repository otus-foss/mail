package com.otus.mail.feature.folders.domain

/**
 * The mailboxes another user shares with a login: one session account with `isPersonal: false`
 * (RFC 8620, section 2) that has mail. [owner] is that account's name, such as the owner's address.
 * Each folder's [MailFolder.sharedIn] is [jmapAccountId], and its `myRights` say what the login may
 * do. These are JMAP shared mailboxes, unlike [SharedFolder], which merges a user's own folders by
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

/** Who shares a mailbox with a login, and whether the login may add mail to it (`mayAddItems`). */
data class SharedBy(
    val owner: String,
    val canEdit: Boolean,
)

/** Where one shared mailbox shows in All accounts, and who shares it. */
data class SharedPlacement(
    /** The account the folder is listed under. */
    val accountId: String,
    val folder: MailFolder,
    val sharedBy: SharedBy,
    /** Listed under its connected owner as the owner's own folder, rather than under the login it is shared with. */
    val underOwner: Boolean = false,
)

/**
 * Lists every shared mailbox once in All accounts. A mailbox whose owner is another connected
 * account, with the same mailbox id there, shows under that owner's own folder; others show under
 * the login that sees them. Owners match by the account name the server gives, or the login's name.
 * Under its owner it can edit when any login it is shared with can.
 */
fun sharedPlacements(accounts: List<AccountFolders>): List<SharedPlacement> {
    val placed =
        accounts.flatMap { viewer ->
            viewer.shared.flatMap { group ->
                val owner = accounts.firstOrNull { it.accountId != viewer.accountId && it.owns(group) }
                group.folders.map { folder ->
                    val sharedBy = SharedBy(group.owner, folder.rights.mayAddItems)
                    val ownFolder = owner?.folder(folder.id)
                    if (ownFolder == null) {
                        SharedPlacement(viewer.accountId, folder, sharedBy)
                    } else {
                        SharedPlacement(owner.accountId, ownFolder, sharedBy, underOwner = true)
                    }
                }
            }
        }
    val (atOwner, atLogin) = placed.partition { it.underOwner }
    return atLogin +
        atOwner.groupBy { it.accountId to it.folder.id }.values.map { same ->
            same.first().let { it.copy(sharedBy = it.sharedBy.copy(canEdit = same.any { p -> p.sharedBy.canEdit })) }
        }
}

private fun AccountFolders.owns(group: SharedMailboxes): Boolean =
    failure == null &&
        listOf(mailboxOwner, accountName).any { it.isNotBlank() && it.equals(group.owner, ignoreCase = true) }
