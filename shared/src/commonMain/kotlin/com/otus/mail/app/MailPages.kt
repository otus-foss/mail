package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.shared.settings.components.LocalFoundSetting
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.messages.domain.within
import com.otus.mail.core.messages.presentation.MessageActions
import com.otus.mail.core.settings.presentation.FolderPillOption
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.folders.presentation.LabeledCopies
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.ListBase
import com.otus.mail.feature.inbox.presentation.folderPillOptions

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

        is MailPage.SettingsPage -> {
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
            ContactsPages(page, states.contacts.cards, states.moveToasts.toasts, navigation, back)
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
    FeedPage(page, MailScope.Role(page.role), states.folderBase(page.role.title), states, dateFormatter, navigation)
}

@Composable
private fun FolderPage(
    page: MailPage.Folder,
    states: MailStates,
    dateFormatter: DateFormatter,
    navigation: MailNavigation,
) {
    val folder = states.folders.all.folder(page.accountId, page.folderId)
    LeaveWhenGone(states.folders.all, gone = folder == null, page.accountId) { navigation.go(MailPage.Inbox) }
    FeedPage(
        page,
        MailScope.Folder(page.accountId, page.folderId),
        states.folderBase(folder?.name ?: "Folder"),
        states,
        dateFormatter,
        navigation,
    )
}

/** The messages [mailScope] loads, on [page], which [base] names. */
@Composable
internal fun FeedPage(
    page: MailPage,
    mailScope: MailScope,
    base: ListBase,
    states: MailStates,
    dateFormatter: DateFormatter,
    navigation: MailNavigation,
) {
    val pageScope = rememberCoroutineScope()
    val feed = remember(mailScope) { states.list.feedOf(mailScope, pageScope) }
    LaunchedEffect(feed) { feed.refresh() }
    ListPage(page, feed, base, states, dateFormatter, navigation)
}

/** A folder page called [name], with the pills the user chose. */
internal fun MailStates.folderBase(name: String): ListBase = ListBase.folder(name, behavior.behavior.list.pills)

/**
 * Runs [leave] when the page's folders are [gone], such as after a delete or rename elsewhere,
 * once the folders of [accountId], or of every account without one, have loaded without failing.
 */
@Composable
internal fun LeaveWhenGone(
    folders: FoldersState,
    gone: Boolean,
    accountId: String? = null,
    leave: () -> Unit,
) {
    val settled = folders.settled(accountId)
    LaunchedEffect(settled, gone) { if (settled && gone) leave() }
}

@Composable
internal fun rememberMessageActions(
    states: MailStates,
    feed: InboxFeedState,
    navigation: MailNavigation,
): MessageActions =
    remember(states, feed, navigation) {
        states.list.messageActions(feed)
    }

/** The folder pills the loaded folders can fill, again whenever they change. */
@Composable
internal fun rememberFolderPills(states: MailStates): List<FolderPillOption> =
    remember(states.folders.all.accounts) { folderPillOptions(states.folders.all.accounts) }

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
    val colors = states.folders.colors.colors to states.behavior.behavior.sidebar.layout.folderColors
    // A message that did not change, as when another sender's avatar arrives, keeps its copy.
    val copies = remember(states.folders.all.accounts, colors) { LabeledCopies() }
    return remember(feed.messages, scope, copies) { states.folders.filing.labeled(feed.messages.within(scope), copies) }
}
