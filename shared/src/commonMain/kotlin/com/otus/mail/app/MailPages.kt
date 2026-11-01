package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.shared.settings.components.LocalFoundSetting
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.within
import com.otus.mail.feature.inbox.presentation.FolderPillOption
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen
import com.otus.mail.feature.inbox.presentation.status

/** The page for [page], which flashes the setting a settings search found there, if any. */
@Composable
internal fun MailPageContent(
    page: MailPage,
    states: MailStates,
    dateFormatter: DateFormatter,
    settings: SharedSettings,
    navigation: MailNavigation,
) {
    val found = navigation.found?.takeIf { it.page == page }?.setting
    CompositionLocalProvider(LocalFoundSetting provides found) {
        PageContent(page, states, dateFormatter, settings, navigation)
    }
}

@Composable
private fun PageContent(
    page: MailPage,
    states: MailStates,
    dateFormatter: DateFormatter,
    settings: SharedSettings,
    navigation: MailNavigation,
) {
    val back = { navigation.back(page) }
    when (page) {
        MailPage.Inbox -> {
            InboxPage(states, dateFormatter, navigation)
        }

        is MailPage.Compose -> {
            ComposePage(page, states, back)
        }

        MailPage.Signatures -> {
            SignaturesPage(states, navigation, back)
        }

        is MailPage.SignatureEditor -> {
            SignatureEditorPage(page, states, back)
        }

        MailPage.Settings, MailPage.EmailAccounts, MailPage.Appearance, MailPage.Behavior, MailPage.Notifications,
        MailPage.NotificationOverrides, MailPage.SidebarSettings, MailPage.PinnedSettings, MailPage.ThemeSettings,
        MailPage.MailListSettings, MailPage.ActionBarSettings, MailPage.Folders, is MailPage.FolderDetail,
        -> {
            SettingsPageContent(page, states, settings, dateFormatter, navigation, back)
        }

        MailPage.Privacy, MailPage.AllowedSenders -> {
            PrivacyPage(page, states, navigation, back)
        }

        is MailPage.Folder -> {
            FolderPage(page, states, dateFormatter, navigation)
        }

        is MailPage.Combined -> {
            CombinedPage(page, states, dateFormatter, navigation)
        }

        MailPage.ReadLater -> {
            ReadLaterPage(states, dateFormatter, navigation)
        }

        MailPage.Conversations -> {
            ConversationsPage(states, dateFormatter, navigation)
        }

        is MailPage.Shared -> {
            SharedFolderPage(page, states, dateFormatter, navigation)
        }

        MailPage.Contacts, is MailPage.Contact, is MailPage.ContactEditor -> {
            ContactsPages(
                page,
                states,
                navigation,
                back,
            )
        }
    }
}

@Composable
private fun CombinedPage(
    page: MailPage.Combined,
    states: MailStates,
    dateFormatter: DateFormatter,
    navigation: MailNavigation,
) {
    val folder = page.folder
    FeedPage(page, MailScope.Role(folder.role, folder.title), folder.title, states, dateFormatter, navigation)
}

@Composable
private fun FolderPage(
    page: MailPage.Folder,
    states: MailStates,
    dateFormatter: DateFormatter,
    navigation: MailNavigation,
) {
    val account = states.folders.account(page.accountId)
    val folder = account?.folder(page.folderId)
    // A folder deleted elsewhere leaves its page once the account's folders have loaded without it.
    LaunchedEffect(account, folder) {
        if (account != null && account.failure == null && folder == null) navigation.go(MailPage.Inbox)
    }
    FeedPage(
        page,
        MailScope.Folder(page.accountId, page.folderId),
        folder?.name ?: "Folder",
        states,
        dateFormatter,
        navigation,
    )
}

/** The messages [mailScope] loads, on [page], which calls their folder [name]. */
@Composable
internal fun FeedPage(
    page: MailPage,
    mailScope: MailScope,
    name: String,
    states: MailStates,
    dateFormatter: DateFormatter,
    navigation: MailNavigation,
) {
    val pageScope = rememberCoroutineScope()
    val feed = remember(mailScope) { states.folderFeed(mailScope, pageScope) }
    LaunchedEffect(feed) { feed.refresh() }
    val filters = rememberPageFilters(states, page, feed)
    val labeled = rememberLabeled(states, feed)
    InboxScreen(
        labeled,
        dateFormatter,
        navigation.openSidebar,
        feed.status(connected = true),
        onRefresh = {
            feed.refresh()
            states.folders.refresh()
        },
        context =
            InboxContext.folder(name, states.behavior.behavior.pills).copy(
                onCompose = { navigation.go(MailPage.Compose(it, parent = page)) },
                loadContent = states::messageContent,
                read = rememberReadContext(states, feed, navigation),
                filters = filters,
                filterOptions = rememberFilterOptions(states, page, filters.applied),
                rowLook = rememberRowLook(states),
                nudge = rememberListNudge(states, labeled),
                onLoadMore = feed::loadMore,
            ),
        messageActions = rememberMessageActions(states, feed, navigation),
    )
}

@Composable
internal fun rememberMessageActions(
    states: MailStates,
    feed: InboxFeedState,
    navigation: MailNavigation,
): MessageActions =
    remember(states, feed, navigation) {
        states.messageActions(feed)
    }

/** The folder pills the loaded folders can fill, again whenever they change. */
@Composable
internal fun rememberFolderPills(states: MailStates): List<FolderPillOption> =
    remember(states.folders.accounts) { folderPillOptions(states.folders.accounts) }

/**
 * The feed's messages that the chosen "Show mail from" scope shows, with their other folders named,
 * again whenever messages, folders, or the scope change.
 */
@Composable
internal fun rememberLabeled(
    states: MailStates,
    feed: InboxFeedState,
): List<MailMessage> {
    val scope = states.mailScope
    val colors = states.folderColors.colors to states.behavior.behavior.sidebar.folderColors
    // A message that did not change, as when another sender's avatar arrives, keeps its copy.
    val copies = remember(states.folders.accounts, colors) { LabeledCopies() }
    return remember(feed.messages, scope, copies) { states.filing.labeled(feed.messages.within(scope), copies) }
}
