package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.otus.dateformat.DateFormatter
import com.otus.mail.feature.folders.domain.CombinedFolder
import com.otus.mail.feature.inbox.domain.INBOX_MAILBOX
import com.otus.mail.feature.inbox.domain.InboxContents
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailboxScope
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.InboxPills
import com.otus.mail.feature.inbox.presentation.InboxStatus
import com.otus.mail.feature.inbox.presentation.MailboxSearch
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen

/**
 * The unified inbox, holding what the user chose in the Inbox settings, whose folder pills load
 * in place of its own mail.
 */
@Composable
internal fun InboxPage(
    states: MailStates,
    dateFormatter: DateFormatter,
    navigation: MailNavigation,
) {
    val feed = states.feed
    val contents = states.inboxContents
    val ownScope = contents.mailScope()
    LaunchedEffect(ownScope) { feed.defaultScope = ownScope }
    val pills =
        InboxPills(
            states.behavior.behavior.pills,
            rememberFolderPills(states),
            foldersLoaded = !states.accounts.loading && !states.folders.loading,
        )
    val labeled = rememberLabeled(states, feed)
    InboxScreen(
        if (feed.mailScope == ownScope) labeled.filter(contents::shows) else labeled,
        dateFormatter,
        navigation.openSidebar,
        InboxStatus(states.accounts.accounts.isNotEmpty(), feed.loading, feed.notice),
        onRefresh = states::refreshInbox,
        context =
            InboxContext(
                defaultSearch = InboxSearch(mailboxes = contents.mailboxNames().map { MailboxScope(null, it) }.toSet()),
                pills = pills,
                mailboxSearch =
                    MailboxSearch(
                        states.mailboxNames(),
                        states.accounts.accounts.map { MailLocation("", it.name, it.id, it.username) },
                        feed::show,
                    ),
                onCompose = { navigation.go(MailPage.Compose(it)) },
                loadHtml = states::messageHtml,
            ),
        messageActions = rememberMessageActions(states, feed, navigation),
    )
}

/** What the Inbox page loads: every account's inbox, and Spam and Trash under the app's names when chosen. */
internal fun InboxContents.mailScope(): MailScope =
    if (roles.size == 1) MailScope.Inbox else MailScope.Roles(roles.associateWith(::roleName))

/** The names the page's messages are filed under, which its default search shows. */
private fun InboxContents.mailboxNames(): List<String> = roles.map(::roleName)

private fun roleName(role: String): String =
    CombinedFolder.entries.firstOrNull { it.role == role }?.title ?: INBOX_MAILBOX
