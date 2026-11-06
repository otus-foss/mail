package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.theme.colors.FolderColor
import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.feature.folders.domain.SharedFolder
import com.otus.mail.feature.folders.domain.sharedFolder
import com.otus.mail.feature.folders.domain.sharedFolders
import com.otus.mail.feature.inbox.presentation.SidebarPinOption
import com.otus.mail.feature.inbox.presentation.screens.PinnedSettingsScreen
import com.otus.mail.feature.inbox.presentation.screens.SidebarSettingsScreen

/** Sidebar settings, or its Pinned page. */
@Composable
internal fun SidebarSettingsPage(
    page: MailPage,
    states: MailStates,
    navigation: MailNavigation,
    onBack: () -> Unit,
) {
    val accounts = states.folders.accounts
    // Not remembered: folder colors change apart from the accounts.
    val options = sidebarPinOptions(accounts, states.folderColors::groupColorOf)
    if (page == MailPage.PinnedSettings) {
        PinnedSettingsScreen(states.behavior, options, onBack)
    } else {
        SidebarSettingsScreen(
            states.behavior,
            options,
            onPinned = { navigation.go(MailPage.PinnedSettings) },
            onBack = onBack,
        )
    }
}

/** Every mailbox the sidebar can show, as Settings lists it; [colorOf] names a folder's icon color. */
internal fun sidebarPinOptions(
    accounts: List<AccountFolders>,
    colorOf: (SharedFolder) -> String?,
): List<SidebarPinOption> =
    pinnableMailboxes(accounts).map { (pin, mailbox) ->
        val folder = mailbox.shared
        SidebarPinOption(
            pin,
            mailbox.title,
            mailbox.icon,
            group = if (folder == null) "Mailboxes" else "Folders",
            path = folder?.path ?: listOf(mailbox.title),
            tint = folder?.let { FolderColor.named(colorOf(it)).tint },
        )
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
    FeedPage(MailPage.ReadLater, folders.toScope(), "Read later", states, dateFormatter, navigation)
}

/**
 * Every conversation: each account's inbox and Sent, kept to threads where the user and someone
 * else both wrote. Sent lists what the user sent whether or not anyone answered.
 */
@Composable
internal fun ConversationsPage(
    states: MailStates,
    dateFormatter: DateFormatter,
    navigation: MailNavigation,
) {
    val scope = MailScope.Roles(listOf(MailboxRole.Inbox, MailboxRole.Sent))
    FeedPage(MailPage.Conversations, scope, "Convos", states, dateFormatter, navigation)
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
    FeedPage(page, folder?.folders.orEmpty().toScope(), folder?.name ?: "Folder", states, dateFormatter, navigation)
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
