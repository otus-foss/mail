package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.CombinedFolder
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.inbox.domain.SidebarPin
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first

/**
 * The page Mail opens on for [choice] before the folders have loaded. Only a system folder whose
 * role Mail does not combine has no page, so it opens the Inbox.
 */
internal fun launchPage(choice: SidebarPin): MailPage =
    when (choice) {
        SidebarPin.Inbox -> MailPage.Inbox
        SidebarPin.ReadLater -> MailPage.ReadLater
        SidebarPin.Conversations -> MailPage.Conversations
        SidebarPin.Contacts -> MailPage.Contacts
        is SidebarPin.Role -> combinedPage(choice.role)
        is SidebarPin.Folder -> MailPage.Shared(choice.key)
    }

private fun combinedPage(role: String): MailPage =
    CombinedFolder.entries.firstOrNull { it.role == role }?.let(MailPage::Combined) ?: MailPage.Inbox

/**
 * The page Mail opens on for [choice] once [accounts] have loaded: the sidebar mailbox's page, or
 * the Inbox when no account has that mailbox any more, such as a renamed folder.
 */
internal fun startPage(
    choice: SidebarPin,
    accounts: List<AccountFolders>,
): MailPage = pinnableMailboxes(accounts).firstOrNull { (pin, _) -> pin == choice }?.second?.page ?: MailPage.Inbox

/**
 * Opens Inbox through [show] when the first folder load of this launch finds that no account has
 * [choice], while [current] still shows the page Mail opened on. An account that failed to load may
 * still have it, so the page stays. Each launch checks once; a recreated activity does not.
 */
@Composable
internal fun StartPageFallback(
    choice: SidebarPin,
    folders: FoldersState,
    current: () -> MailPage,
    show: (MailPage) -> Unit,
) {
    var checked by rememberSaveable { mutableStateOf(choice == SidebarPin.Inbox) }
    if (checked) return
    LaunchedEffect(folders) {
        snapshotFlow { folders.loading }
            .dropWhile { loading -> !loading }
            .first { loading -> !loading }
        checked = true
        val accounts = folders.accounts
        if (accounts.none { it.failure != null } && current() == launchPage(choice)) show(startPage(choice, accounts))
    }
}
