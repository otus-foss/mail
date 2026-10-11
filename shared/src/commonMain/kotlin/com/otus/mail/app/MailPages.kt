package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.otus.accounts.domain.AccountService
import com.otus.accounts.presentation.screens.AccountSetupScreen
import com.otus.dateformat.DateFormatter
import com.otus.mail.feature.folders.presentation.screens.FoldersScreen
import com.otus.mail.feature.folders.presentation.screens.RuleEditorScreen
import com.otus.mail.feature.folders.presentation.screens.RulesScreen
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.InboxStatus
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.components.MailBehaviorSettings
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
            val feed = states.feed
            InboxScreen(
                rememberLabeled(states, feed),
                dateFormatter,
                navigation.openSidebar,
                InboxStatus(states.accounts.accounts.isNotEmpty(), feed.loading, feed.notice),
                onRefresh = states::refreshInbox,
                messageActions = rememberMessageActions(states, feed, navigation),
            )
        }

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
            AppearanceSettingsScreen(dateFormatState, onBack = back) { MailBehaviorSettings(states.behavior) }
        }

        MailPage.Folders -> {
            FoldersScreen(states.folders, onBack = back) { navigation.go(MailPage.Folder(it.accountId, it.id)) }
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

        is MailPage.Folder -> {
            FolderPage(page, states, dateFormatter, navigation)
        }

        is MailPage.Combined -> {
            val folder = page.folder
            FeedPage(MailScope.Role(folder.role, folder.title), folder.title, states, dateFormatter, navigation)
        }
    }
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
        MailScope.Folder(page.accountId, page.folderId),
        folder?.name ?: "Folder",
        states,
        dateFormatter,
        navigation,
    )
}

/** The messages [mailScope] loads, on a page that calls their folder [name]. */
@Composable
private fun FeedPage(
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
        context = InboxContext.folder(name),
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

/** The feed's messages with their other folders named, again whenever messages or folders change. */
@Composable
private fun rememberLabeled(
    states: MailStates,
    feed: InboxFeedState,
): List<MailMessage> = remember(feed.messages, states.folders.accounts) { states.filing.labeled(feed.messages) }
