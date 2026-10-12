package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.otus.accounts.domain.AccountService
import com.otus.accounts.presentation.screens.AccountSetupScreen
import com.otus.dateformat.DateFormatter
import com.otus.mail.feature.folders.domain.sharedFolders
import com.otus.mail.feature.folders.presentation.screens.FoldersScreen
import com.otus.mail.feature.folders.presentation.screens.RuleEditorScreen
import com.otus.mail.feature.folders.presentation.screens.RulesScreen
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.presentation.FolderPillOption
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.InboxPills
import com.otus.mail.feature.inbox.presentation.InboxStatus
import com.otus.mail.feature.inbox.presentation.MailboxSearch
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.components.MailBehaviorSettings
import com.otus.mail.feature.inbox.presentation.screens.ActionBarSettingsScreen
import com.otus.mail.feature.inbox.presentation.screens.ComposeScreen
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen
import com.otus.settings.presentation.DateFormatSettingsState
import com.otus.settings.presentation.screens.AppearanceSettingsScreen
import com.otus.settings.presentation.screens.SettingsScreen

@Composable
internal fun MailPageContent(
    page: MailPage,
    states: MailStates,
    dateFormatter: DateFormatter,
    dateFormatState: DateFormatSettingsState,
    navigation: MailNavigation,
) {
    val back = { navigation.go(page.parent ?: page) }
    when (page) {
        MailPage.Inbox -> {
            InboxPage(states, dateFormatter, navigation)
        }

        is MailPage.Compose -> {
            ComposePage(page, states, back)
        }

        MailPage.Settings, MailPage.EmailAccounts, MailPage.Appearance, MailPage.SidebarSettings,
        MailPage.ActionBarSettings, MailPage.Folders, MailPage.Rules, is MailPage.RuleEditor,
        -> {
            SettingsPageContent(page, states, dateFormatState, navigation, back)
        }

        is MailPage.Folder -> {
            FolderPage(page, states, dateFormatter, navigation)
        }

        is MailPage.Combined -> {
            val folder = page.folder
            FeedPage(page, MailScope.Role(folder.role, folder.title), folder.title, states, dateFormatter, navigation)
        }

        MailPage.ReadLater -> {
            ReadLaterPage(states, dateFormatter, navigation)
        }

        is MailPage.Shared -> {
            SharedFolderPage(page, states, dateFormatter, navigation)
        }
    }
}

/** Settings and its subpages. */
@Composable
private fun SettingsPageContent(
    page: MailPage,
    states: MailStates,
    dateFormatState: DateFormatSettingsState,
    navigation: MailNavigation,
    back: () -> Unit,
) {
    when (page) {
        MailPage.Settings -> {
            SettingsScreen(
                entries = mailSettingsEntries(states.accounts.accounts.size, navigation),
                onOpenNavigation = navigation.openSidebar,
            )
        }

        MailPage.EmailAccounts -> {
            AccountSetupScreen(states.accounts, "Email accounts", AccountService.Mail, onBack = back)
        }

        MailPage.Appearance -> {
            val folderPills = rememberFolderPills(states)
            AppearanceSettingsScreen(
                dateFormatState,
                onBack = back,
            ) {
                MailBehaviorSettings(
                    states.behavior,
                    folderPills,
                    onSidebar = { navigation.go(MailPage.SidebarSettings) },
                    onOpenActionBar = { navigation.go(MailPage.ActionBarSettings) },
                )
            }
        }

        MailPage.SidebarSettings -> {
            SidebarSettingsPage(states, back)
        }

        MailPage.ActionBarSettings -> {
            ActionBarSettingsScreen(states.behavior, rememberFolderPaths(states), onBack = back)
        }

        MailPage.Folders -> {
            FoldersScreen(states.folders, states.rules, states.folderSenders, onBack = back) {
                navigation.go(MailPage.Folder(it.accountId, it.id))
            }
        }

        MailPage.Rules -> {
            RulesScreen(states.rules, states.folders, onBack = back) { accountId, ruleId ->
                states.ruleEditor.edit(accountId, ruleId)
                navigation.go(MailPage.RuleEditor(accountId, ruleId))
            }
        }

        is MailPage.RuleEditor -> {
            RuleEditorScreen(
                states.ruleEditor,
                states.rules,
                states.folders,
                page.accountId,
                page.ruleId,
                onDone = back,
            )
        }

        else -> {}
    }
}

/** A draft for the page's request; without an account there is nothing to write from. */
@Composable
private fun ComposePage(
    page: MailPage.Compose,
    states: MailStates,
    onClose: () -> Unit,
) {
    val compose = remember(page) { states.compose(page.request) } ?: return LaunchedEffect(page) { onClose() }
    ComposeScreen(compose, states.behavior.behavior, onClose)
}

/** The unified inbox, whose folder pills load in place of its own mail. */
@Composable
private fun InboxPage(
    states: MailStates,
    dateFormatter: DateFormatter,
    navigation: MailNavigation,
) {
    val feed = states.feed
    val mailboxes = states.mailboxNames()
    val pills =
        InboxPills(
            states.behavior.behavior.pills,
            rememberFolderPills(states),
            foldersLoaded = !states.accounts.loading && !states.folders.loading,
        )
    InboxScreen(
        rememberLabeled(states, feed),
        dateFormatter,
        navigation.openSidebar,
        InboxStatus(states.accounts.accounts.isNotEmpty(), feed.loading, feed.notice),
        onRefresh = states::refreshInbox,
        context =
            InboxContext(
                pills = pills,
                mailboxSearch =
                    MailboxSearch(
                        mailboxes,
                        states.accounts.accounts.map { MailLocation("", it.name, it.id, it.username) },
                        feed::show,
                    ),
                onCompose = { navigation.go(MailPage.Compose(it)) },
            ),
        messageActions = rememberMessageActions(states, feed, navigation),
    )
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
    val feed = remember(mailScope) { states.folderFeed(mailScope) }
    LaunchedEffect(feed) { feed.refresh() }
    InboxScreen(
        rememberLabeled(states, feed),
        dateFormatter,
        navigation.openSidebar,
        InboxStatus(connected = true, loading = feed.loading, notice = feed.notice),
        onRefresh = {
            feed.refresh()
            states.folders.refresh()
        },
        context =
            InboxContext.folder(name, states.behavior.behavior.pills).copy(
                onCompose = { navigation.go(MailPage.Compose(it, parent = page)) },
            ),
        messageActions = rememberMessageActions(states, feed, navigation),
    )
}

@Composable
private fun rememberMessageActions(
    states: MailStates,
    feed: InboxFeedState,
    navigation: MailNavigation,
): MessageActions =
    remember(states, feed, navigation) {
        states.messageActions(feed) { accountId -> navigation.go(MailPage.RuleEditor(accountId, null)) }
    }

/** The path of each of every account's own folders, for Move to buttons. */
@Composable
private fun rememberFolderPaths(states: MailStates): List<List<String>> =
    remember(
        states.folders.accounts,
    ) { sharedFolders(states.folders.accounts).flatMap { it.flatten() }.map { it.path } }

/** The folder pills the loaded folders can fill, again whenever they change. */
@Composable
private fun rememberFolderPills(states: MailStates): List<FolderPillOption> =
    remember(states.folders.accounts) { folderPillOptions(states.folders.accounts) }

/** The feed's messages with their other folders named, again whenever messages or folders change. */
@Composable
private fun rememberLabeled(
    states: MailStates,
    feed: InboxFeedState,
): List<MailMessage> = remember(feed.messages, states.folders.accounts) { states.filing.labeled(feed.messages) }
