package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.theme.colors.FolderColor
import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.SharedFolder
import com.otus.mail.core.mailbox.presentation.READ_LATER_TITLE
import com.otus.mail.core.mailbox.sharedFolder
import com.otus.mail.core.mailbox.sharedFolders
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.settings.presentation.SidebarPinOption
import com.otus.mail.feature.inbox.presentation.ListBase
import com.otus.mail.feature.inbox.presentation.conversationsScope
import com.otus.mail.feature.mailsettings.presentation.screens.PinnedSettingsScreen
import com.otus.mail.feature.mailsettings.presentation.screens.SidebarSettingsScreen
import com.otus.mail.feature.sidebar.presentation.pinnableMailboxes

/** Sidebar settings, or its Pinned page. */
@Composable
internal fun SidebarSettingsPage(
    page: MailPage,
    states: MailStates,
    navigation: MailNavigation,
    onBack: () -> Unit,
) {
    val accounts = states.folders.all.accounts
    // Not remembered: folder colors change apart from the accounts.
    val options = sidebarPinOptions(accounts, states.folders.colors::groupColorOf)
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
    pinnableMailboxes(accounts).map { mailbox ->
        val folder = mailbox.shared
        SidebarPinOption(
            mailbox.pin,
            mailbox.title,
            mailbox.icon,
            group = if (folder == null) "Mailboxes" else "Folders",
            path = folder?.path ?: listOf(mailbox.title),
            tint = folder?.let { FolderColor.named(colorOf(it)).tint },
        )
    }

@Composable
internal fun ReadLaterPage(
    states: MailStates,
    dateFormatter: DateFormatter,
    navigation: MailNavigation,
) {
    val folders =
        states.folders.all.accounts
            .mapNotNull { it.readLater }
    LeaveWhenGone(states.folders.all, gone = folders.isEmpty()) { navigation.go(MailPage.Inbox) }
    FeedPage(
        MailPage.ReadLater,
        folders.toScope(),
        states.folderBase(READ_LATER_TITLE),
        states,
        dateFormatter,
        navigation,
    )
}

@Composable
internal fun ConversationsPage(
    states: MailStates,
    dateFormatter: DateFormatter,
    navigation: MailNavigation,
) {
    FeedPage(
        MailPage.Conversations,
        conversationsScope,
        ListBase.conversations(states.behavior.behavior.list.pills),
        states,
        dateFormatter,
        navigation,
    )
}

/** One of the user's folders, holding each account's folder at its path. */
@Composable
internal fun SharedFolderPage(
    page: MailPage.Shared,
    states: MailStates,
    dateFormatter: DateFormatter,
    navigation: MailNavigation,
) {
    val accounts = states.folders.all.accounts
    val folder = remember(accounts, page) { sharedFolder(sharedFolders(accounts), page.key) }
    LeaveWhenGone(states.folders.all, gone = folder == null) { navigation.go(MailPage.Inbox) }
    FeedPage(
        page,
        folder?.folders.orEmpty().toScope(),
        states.folderBase(folder?.name ?: "Folder"),
        states,
        dateFormatter,
        navigation,
    )
}

private fun List<MailFolder>.toScope(): MailScope =
    MailScope.Folders(map { MailScope.Folder(it.accountId, it.id) }.toSet())
