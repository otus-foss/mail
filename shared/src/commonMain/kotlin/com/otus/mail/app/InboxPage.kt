package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.otus.dateformat.DateFormatter
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.messages.domain.MailboxScope
import com.otus.mail.core.messages.domain.inScope
import com.otus.mail.core.settings.domain.InboxContents
import com.otus.mail.core.settings.domain.SidebarPin
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.InboxPills
import com.otus.mail.feature.inbox.presentation.MailboxSearch
import com.otus.mail.feature.inbox.presentation.QuickJumpTarget
import com.otus.mail.feature.inbox.presentation.QuickViewContext
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen
import com.otus.mail.feature.inbox.presentation.status
import com.otus.mail.feature.sidebar.presentation.sidebarMailboxes

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
    val contents = states.behavior.behavior.inbox
    val ownScope = contents.mailScope()
    LaunchedEffect(ownScope) { feed.defaultScope = ownScope }
    val pills =
        InboxPills(
            states.behavior.behavior.pills,
            rememberFolderPills(states),
            foldersLoaded = !states.accounts.loading && !states.folders.loading,
        )
    val labeled = rememberLabeled(states, feed)
    val filters = rememberPageFilters(states, MailPage.Inbox, feed)
    val rowLook = rememberRowLook(states)
    val messageActions = rememberMessageActions(states, feed, navigation)
    InboxScreen(
        labeled,
        dateFormatter,
        navigation.openSidebar,
        feed.status(connected = states.accounts.accounts.isNotEmpty()),
        onRefresh = states::refreshInbox,
        context =
            InboxContext(
                defaultSearch = InboxSearch(mailboxes = contents.mailboxScopes().toSet()),
                pills = pills,
                mailboxSearch =
                    MailboxSearch(
                        states.searchMailboxes(),
                        states.accounts.accounts.map { MailLocation("", it.name, it.id, it.username) },
                        feed::show,
                    ),
                onCompose = { navigation.go(MailPage.Compose(it)) },
                filters = filters,
                filterOptions = rememberFilterOptions(states, MailPage.Inbox, filters.applied),
                rowLook = rowLook,
                readView = rememberReadView(states, feed, navigation, MailPage.Inbox, messageActions, rowLook.own),
                nudge = rememberListNudge(states, labeled),
                onLoadMore = feed::loadMore,
                scope = states.scopeSwitch(states.mailScope, states.chooseScope),
                quick = rememberQuickView(states, navigation),
                opening = states.requests.row,
            ),
        messageActions = messageActions,
    )
}

/**
 * What the Inbox page loads: every account's inbox, the chosen system folders under the app's
 * names, and the user's own folders when chosen.
 */
internal fun InboxContents.mailScope(): MailScope =
    if (roles.size == 1 && !ownFolders) {
        MailScope.Inbox
    } else {
        MailScope.Roles(roles, ownFolders)
    }

/**
 * The names the page's messages are filed under, which its default search shows. Own folders have
 * the user's names, so with them on the search starts from every mailbox the page loaded.
 */
private fun InboxContents.mailboxScopes(): List<MailboxScope> =
    if (ownFolders) emptyList() else roles.map { MailboxScope(null, it.title, it) }

/**
 * What pulling the Inbox's control panel down shows: the modules picked in settings, and Jump to
 * with the sidebar's pins in their order, leaving out the Inbox the page already is.
 */
@Composable
private fun rememberQuickView(
    states: MailStates,
    navigation: MailNavigation,
): QuickViewContext {
    val behavior = states.behavior.behavior
    val folders = states.folders.accounts.inScope(states.mailScope)
    val pinned =
        remember(folders, behavior.inbox, behavior.sidebar) {
            sidebarMailboxes(folders, behavior.inbox, behavior.sidebar).filterNot { it.pin == SidebarPin.Inbox }
        }
    return QuickViewContext(
        behavior.quickView,
        pinned.map { mailbox ->
            QuickJumpTarget(mailbox.title, mailbox.icon) { navigation.go(launchPage(mailbox.pin)) }
        },
    )
}
