package com.otus.mail.feature.inbox.presentation

import com.otus.avatar.ResolvedAvatar
import com.otus.avatar.generatedAvatar
import com.otus.avatar.ui.presentation.toChipAvatar
import com.otus.designsystem.shared.people.components.ChipAvatar
import com.otus.designsystem.theme.colors.FolderColor
import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.FolderKey
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.folderTree
import com.otus.mail.core.mailbox.presentation.symbol
import com.otus.mail.core.mailbox.subtreeIds
import com.otus.mail.core.messages.domain.AccountAddresses
import com.otus.mail.core.messages.domain.MailScopeChoice
import com.otus.mail.core.messages.domain.OwnAddress
import com.otus.mail.core.settings.domain.FilterLayout
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.folderIds

/**
 * What the filter panel offers on a page of the [accounts] "Show mail from" picks, [choice]: their
 * [addresses], or only the picked one, and their folders in [colorOf]. Only a page [byFolder]
 * filters by folder.
 */
fun filterOptions(
    accounts: List<AccountFolders>,
    addresses: List<AccountAddresses>,
    choice: MailScopeChoice,
    colorOf: (MailFolder) -> FolderColor,
    byFolder: Boolean,
    layout: FilterLayout,
): FilterOptions {
    val shown = accounts.filter { it.failure == null && (choice.accountId == null || it.accountId == choice.accountId) }
    return FilterOptions(
        addresses = filterAddresses(addresses, choice),
        accounts = shown.map { it.filterAccount(colorOf) },
        folders = byFolder,
        readLater = shown.any { it.readLaterId != null },
        layout = layout,
    )
}

/**
 * What the server needs to run [filters]: chosen folders of [accounts] with their subfolders, each
 * account's Read later folder, the user's [addresses], and the [logins] "Show mail from" picks.
 * Default filters need none of it, so they never load the list again.
 */
fun mailQuery(
    filters: InboxFilters,
    accounts: List<AccountFolders>,
    addresses: List<AccountAddresses>,
    logins: Set<String>?,
): MailQuery {
    if (filters.isDefault) return MailQuery()
    val descendants = { login: String, id: String ->
        val account = accounts.firstOrNull { it.accountId == login }
        account?.folder(id)?.let { subtreeIds(account.folders, it) - id }.orEmpty()
    }
    return MailQuery(
        filters = filters,
        folderIds = folderIds(filters.folders, descendants),
        readLaterIds = accounts.mapNotNull { account -> account.readLaterId?.let { account.accountId to it } }.toMap(),
        addresses = addresses.associate { it.accountId to it.addresses.toSet() },
        logins = logins,
    )
}

private fun filterAddresses(
    addresses: List<AccountAddresses>,
    choice: MailScopeChoice,
): List<FilterAddress> =
    addresses
        .filter { choice.accountId == null || it.accountId == choice.accountId }
        .flatMap { account ->
            account.addresses
                .filter { choice !is MailScopeChoice.Address || it.equals(choice.address, ignoreCase = true) }
                .map { FilterAddress(OwnAddress(account.accountId, it), avatar(account.name, it)) }
        }

private fun AccountFolders.filterAccount(color: (MailFolder) -> FolderColor): FilterAccount =
    FilterAccount(
        accountId,
        accountName,
        avatar(accountName, accountName),
        folderTree().map { node ->
            val folder = node.folder
            FilterFolder(
                key = FolderKey(accountId, folder.id),
                name = folder.name,
                ancestors = node.path.dropLast(1),
                icon = node.symbol,
                color = color(folder),
                system = !folder.isCustom || node.readLater,
                parentId = folder.parentId,
                descendants = subtreeIds(folders, folder) - folder.id,
            )
        },
    )

private fun avatar(
    name: String,
    address: String,
): ChipAvatar = ResolvedAvatar(generatedAvatar(name, address)).toChipAvatar()
