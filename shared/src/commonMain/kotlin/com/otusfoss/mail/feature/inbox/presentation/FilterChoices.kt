package com.otusfoss.mail.feature.inbox.presentation

import com.otusfoss.core.avatar.ResolvedAvatar
import com.otusfoss.core.avatar.generatedAvatar
import com.otusfoss.core.avatar.ui.presentation.toChipAvatar
import com.otusfoss.core.designsystem.shared.people.components.ChipAvatar
import com.otusfoss.core.designsystem.theme.colors.FolderColor
import com.otusfoss.mail.core.mailbox.AccountFolders
import com.otusfoss.mail.core.mailbox.FolderKey
import com.otusfoss.mail.core.mailbox.MailFolder
import com.otusfoss.mail.core.mailbox.folderTree
import com.otusfoss.mail.core.mailbox.presentation.symbol
import com.otusfoss.mail.core.mailbox.subtreeIds
import com.otusfoss.mail.core.messages.domain.AccountAddresses
import com.otusfoss.mail.core.messages.domain.MailScopeChoice
import com.otusfoss.mail.core.messages.domain.OwnAddress
import com.otusfoss.mail.core.settings.domain.FilterLayout
import com.otusfoss.mail.feature.inbox.domain.InboxFilters
import com.otusfoss.mail.feature.inbox.domain.ListKind
import com.otusfoss.mail.feature.inbox.domain.MailQuery
import com.otusfoss.mail.feature.inbox.domain.folderIds

/**
 * What the filter panel offers on a page of the [accounts] "Show mail from" picks, [choice]: their
 * [addresses], or only the picked one, and their folders in [colorOf]. The page's [kind] decides
 * which filters it offers; only the Inbox filters by folder, as a folder's page already shows one.
 */
fun filterOptions(
    accounts: List<AccountFolders>,
    addresses: List<AccountAddresses>,
    choice: MailScopeChoice,
    colorOf: (MailFolder) -> FolderColor,
    kind: ListKind,
    layout: FilterLayout,
): FilterOptions {
    val shown = accounts.filter { it.failure == null && (choice.accountId == null || it.accountId == choice.accountId) }
    return FilterOptions(
        addresses = filterAddresses(addresses, choice),
        accounts = shown.map { it.filterAccount(colorOf) },
        folders = kind == ListKind.Inbox,
        readLater = shown.any { it.readLaterId != null },
        layout = layout,
        kind = kind,
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
