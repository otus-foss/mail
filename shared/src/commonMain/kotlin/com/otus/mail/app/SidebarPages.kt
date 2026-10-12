package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.otus.dateformat.DateFormatter
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.sharedFolder
import com.otus.mail.feature.folders.domain.sharedFolders
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.presentation.screens.SidebarSettingsScreen

@Composable
internal fun SidebarSettingsPage(
    states: MailStates,
    onBack: () -> Unit,
) {
    val accounts = states.folders.accounts
    val mailboxes = remember(accounts) { sidebarMailboxes(accounts).map { it.title } }
    SidebarSettingsScreen(states.behavior, mailboxes, onBack)
}

/** Every account's Read later folder. */
@Composable
internal fun ReadLaterPage(
    states: MailStates,
    dateFormatter: DateFormatter,
    navigation: MailNavigation,
) {
    val folders = states.folders.accounts.mapNotNull { it.readLater }
    LeaveWhenGone(states, navigation, gone = folders.isEmpty())
    FeedPage(folders.toScope(), "Read later", states, dateFormatter, navigation)
}

/** One of the user's folders, holding each account's folder at its path. */
@Composable
internal fun SharedFolderPage(
    page: MailPage.Shared,
    states: MailStates,
    dateFormatter: DateFormatter,
    navigation: MailNavigation,
) {
    val accounts = states.folders.accounts
    val folder = remember(accounts, page) { sharedFolder(sharedFolders(accounts), page.key) }
    LeaveWhenGone(states, navigation, gone = folder == null)
    FeedPage(folder?.folders.orEmpty().toScope(), folder?.name ?: "Folder", states, dateFormatter, navigation)
}

/**
 * Opens Inbox when the page's folders are [gone], such as after a rename elsewhere, once every
 * account's folders have loaded. An account that failed to load may still hold them, so the page stays.
 */
@Composable
private fun LeaveWhenGone(
    states: MailStates,
    navigation: MailNavigation,
    gone: Boolean,
) {
    val accounts = states.folders.accounts
    val loaded = !states.folders.loading && accounts.isNotEmpty() && accounts.none { it.failure != null }
    LaunchedEffect(loaded, gone) {
        if (loaded && gone) navigation.go(MailPage.Inbox)
    }
}

private fun List<MailFolder>.toScope(): MailScope =
    MailScope.Folders(map { MailScope.Folder(it.accountId, it.id) }.toSet())
