package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otus.dateformat.DateFormatter
import com.otus.mail.core.messages.domain.inScope
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.QuickJumpTarget
import com.otus.mail.feature.inbox.presentation.QuickViewContext
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen
import com.otus.mail.feature.inbox.presentation.status
import com.otus.mail.feature.sidebar.presentation.sidebarMailboxes

/**
 * A list of [feed]'s mail on [page], the Inbox and every folder page alike: [base] names the list
 * and says what its search starts from, and every page gets the same Read view, filters, quick
 * view, and scope switcher.
 */
@Composable
internal fun ListPage(
    page: MailPage,
    feed: InboxFeedState,
    base: InboxContext,
    states: MailStates,
    dateFormatter: DateFormatter,
    navigation: MailNavigation,
) {
    val labeled = rememberLabeled(states, feed)
    val filters = rememberPageFilters(states, page, feed)
    val rowLook = rememberRowLook(states)
    val messageActions = rememberMessageActions(states, feed, navigation)
    InboxScreen(
        labeled,
        dateFormatter,
        navigation.openSidebar,
        feed.status(connected = states.accounts.accounts.isNotEmpty()),
        onRefresh = {
            feed.refresh()
            states.folders.refresh()
        },
        context =
            base.copy(
                onCompose = { navigation.go(MailPage.Compose(it, parent = page)) },
                filters = filters,
                filterOptions = rememberFilterOptions(states, page, filters.applied),
                rowLook = rowLook,
                readView = rememberReadView(states, feed, navigation, page, messageActions, rowLook.own),
                nudge = rememberListNudge(states, labeled),
                onLoadMore = feed::loadMore,
                scope = states.scopeSwitch(states.mailScope, states.chooseScope),
                quick = rememberQuickView(states, page, navigation),
                // A tapped notification opens its message in the feed MessageOpens loads, the Inbox's.
                opening = states.requests.row.takeIf { feed === states.feed },
            ),
        messageActions = messageActions,
    )
}

/**
 * What pulling a list's control panel down shows: the modules picked in settings, and Jump to
 * with the sidebar's pins in their order, leaving out the [page] the list already is.
 */
@Composable
private fun rememberQuickView(
    states: MailStates,
    page: MailPage,
    navigation: MailNavigation,
): QuickViewContext {
    val behavior = states.behavior.behavior
    val folders = states.folders.accounts.inScope(states.mailScope)
    val pinned =
        remember(folders, behavior.inbox, behavior.sidebar, page) {
            sidebarMailboxes(folders, behavior.inbox, behavior.sidebar).filterNot { it.pin == page.pin }
        }
    return QuickViewContext(
        behavior.quickView,
        pinned.map { mailbox ->
            QuickJumpTarget(mailbox.title, mailbox.icon) { navigation.go(launchPage(mailbox.pin)) }
        },
    )
}
