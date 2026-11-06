package com.otus.mail.app

import com.otus.avatar.ResolvedAvatar
import com.otus.avatar.generatedAvatar
import com.otus.avatar.ui.presentation.toChipAvatar
import com.otus.designsystem.shared.people.components.ChipAvatar
import com.otus.designsystem.theme.colors.FolderColor
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.ancestors
import com.otus.mail.feature.folders.domain.folderTree
import com.otus.mail.feature.folders.domain.subtreeIds
import com.otus.mail.feature.folders.presentation.folderIcon
import com.otus.mail.feature.inbox.domain.MailScopeChoice
import com.otus.mail.feature.inbox.domain.OwnAddress
import com.otus.mail.feature.inbox.presentation.FilterAccount
import com.otus.mail.feature.inbox.presentation.FilterAddress
import com.otus.mail.feature.inbox.presentation.FilterFolder
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.FolderKey

/**
 * What the filter panel offers on a page of the accounts "Show mail from" picks: their addresses,
 * or only the picked one, and their folders. Only the Inbox filters by folder; a folder's page
 * already shows one.
 */
internal fun MailStates.filterOptions(page: MailPage): FilterOptions {
    val choice = mailScope
    val shown =
        folders.accounts.filter {
            it.failure == null &&
                (choice.accountId == null || it.accountId == choice.accountId)
        }
    val accounts = shown.map { it.filterAccount(::folderColor) }
    return FilterOptions(
        addresses = filterAddresses(choice),
        accounts = accounts,
        folders = page == MailPage.Inbox,
        readLater = shown.any { it.readLaterId != null },
        layout = behavior.behavior.filters,
    )
}

private fun MailStates.folderColor(folder: MailFolder): FolderColor = FolderColor.named(folderColors.colorOf(folder))

private fun MailStates.filterAddresses(choice: MailScopeChoice): List<FilterAddress> =
    accountAddresses()
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
                ancestors = ancestors(folders, folder).reversed().map { it.name },
                icon = folderIcon(node),
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
