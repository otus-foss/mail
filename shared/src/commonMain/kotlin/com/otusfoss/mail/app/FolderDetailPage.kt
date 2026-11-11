package com.otusfoss.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otusfoss.core.designsystem.shared.pages.components.Subpage
import com.otusfoss.mail.feature.folders.domain.AddressField
import com.otusfoss.mail.feature.folders.presentation.KnownAddress
import com.otusfoss.mail.feature.folders.presentation.screens.FolderDetailScreen
import com.otusfoss.mail.feature.folders.presentation.screens.FolderDetailStates

/** One folder's settings page, opened from Folders & rules. A folder that is gone leads back. */
@Composable
internal fun FolderDetailPage(
    page: MailPage.FolderDetail,
    states: MailStates,
    navigation: MailNavigation,
    back: () -> Unit,
) {
    val folder = states.folders.all.folder(page.accountId, page.folderId, page.sharedIn)
    LeaveWhenGone(states.folders.all, gone = folder == null, page.accountId, back)
    if (folder == null) return Subpage("", back) {}
    val detail =
        remember(states) { FolderDetailStates(states.folders.all, states.folders.colors, states.folders.rules) }
    val known = remember(states.list.feed.messages, page.accountId) { states.knownAddresses(page.accountId) }
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
    list.feed.messages
        .filter { it.location.accountId == accountId }
        .flatMap { message ->
            listOf(KnownAddress(AddressField.From, message.sender.address, message.sender.name)) +
                message.to.map { KnownAddress(AddressField.To, it.address, it.name) }
        }
