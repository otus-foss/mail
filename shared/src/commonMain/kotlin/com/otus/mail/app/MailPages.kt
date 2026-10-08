package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.otus.mail.core.dateformat.DateFormatter
import com.otus.mail.feature.accounts.presentation.screens.AccountSetupScreen
import com.otus.mail.feature.folders.presentation.screens.FoldersScreen
import com.otus.mail.feature.folders.presentation.screens.RuleEditorScreen
import com.otus.mail.feature.folders.presentation.screens.RulesScreen
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.InboxStatus
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen
import com.otus.mail.feature.settings.presentation.DateFormatSettingsState
import com.otus.mail.feature.settings.presentation.screens.AppearanceSettingsScreen
import com.otus.mail.feature.settings.presentation.screens.SettingsScreen

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
                feed.messages,
                dateFormatter,
                navigation.openSidebar,
                InboxStatus(states.accounts.accounts.isNotEmpty(), feed.loading, feed.notice),
                onRefresh = states::refreshInbox,
                messageActions = rememberMessageActions(states, feed, navigation),
            )
        }

        MailPage.Settings -> {
            SettingsScreen(
                dateFormatState,
                accountsSummary = accountsSummary(states.accounts.accounts.size),
                onOpenNavigation = navigation.openSidebar,
                onOpenAccounts = { navigation.go(MailPage.EmailAccounts) },
                onOpenAppearance = { navigation.go(MailPage.Appearance) },
                onOpenFolders = { navigation.go(MailPage.Folders) },
                onOpenRules = { navigation.go(MailPage.Rules) },
            )
        }

        MailPage.EmailAccounts -> {
            AccountSetupScreen(states.accounts, onBack = back)
        }

        MailPage.Appearance -> {
            AppearanceSettingsScreen(dateFormatState, onBack = back)
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
    }
}

@Composable
private fun FolderPage(
    page: MailPage.Folder,
    states: MailStates,
    dateFormatter: DateFormatter,
    navigation: MailNavigation,
) {
    val feed = remember(page) { states.folderFeed(page) }
    LaunchedEffect(feed) { feed.refresh() }
    val account = states.folders.account(page.accountId)
    val folder = account?.folder(page.folderId)
    // A folder deleted elsewhere leaves its page once the account's folders have loaded without it.
    LaunchedEffect(account, folder) {
        if (account != null && account.failure == null && folder == null) navigation.go(MailPage.Inbox)
    }
    InboxScreen(
        feed.messages,
        dateFormatter,
        navigation.openSidebar,
        InboxStatus(connected = true, loading = feed.loading, notice = feed.notice),
        onRefresh = {
            feed.refresh()
            states.folders.refresh()
        },
        context = InboxContext.folder(folder?.name ?: "Folder"),
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

private fun accountsSummary(count: Int): String =
    when (count) {
        0 -> "Connect a JMAP server"
        1 -> "1 account connected"
        else -> "$count accounts connected"
    }
