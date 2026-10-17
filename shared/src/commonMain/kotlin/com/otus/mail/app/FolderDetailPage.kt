package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.otus.designsystem.components.Subpage
import com.otus.mail.feature.folders.domain.AddressField
import com.otus.mail.feature.folders.presentation.KnownAddress
import com.otus.mail.feature.folders.presentation.screens.FolderDetailScreen
import com.otus.mail.feature.folders.presentation.screens.FolderDetailStates

/** One folder's settings page, opened from Folders & rules. A folder that is gone leads back. */
@Composable
internal fun FolderDetailPage(
    page: MailPage.FolderDetail,
    states: MailStates,
    navigation: MailNavigation,
    back: () -> Unit,
) {
    val account = states.folders.account(page.accountId)
    val folder = states.folders.folder(page.accountId, page.folderId, page.sharedIn)
    // Deleting the folder, here or elsewhere, removes it from the next load.
    LaunchedEffect(account, folder) { if (account != null && account.failure == null && folder == null) back() }
    if (folder == null) return Subpage("", back) {}
    val detail = remember(states) { FolderDetailStates(states.folders, states.folderColors, states.rules) }
    val known = remember(states.feed.messages, page.accountId) { states.knownAddresses(page.accountId) }
    FolderDetailScreen(
        folder,
        detail,
        known,
        onOpenFolder = { navigation.go(MailPage.FolderDetail(it.accountId, it.id, it.sharedIn)) },
        onBack = back,
    )
}

/** The senders and recipients of [accountId]'s loaded mail, once per message, for rule suggestions. */
internal fun MailStates.knownAddresses(accountId: String): List<KnownAddress> =
    feed.messages
        .filter { it.location.accountId == accountId }
        .flatMap { message ->
            listOf(KnownAddress(AddressField.From, message.sender.address, message.sender.name)) +
                message.to.map { KnownAddress(AddressField.To, it.address, it.name) }
        }
