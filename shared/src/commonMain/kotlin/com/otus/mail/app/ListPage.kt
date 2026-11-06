package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otus.dateformat.DateFormatter
import com.otus.mail.core.messages.domain.inScope
import com.otus.mail.core.settings.domain.InboxPill
import com.otus.mail.core.settings.presentation.InboxPills
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.MailboxSearch
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
    base: ListBase,
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
            states.folders.all.refresh()
        },
        context =
            InboxContext(
                defaultSearch = base.defaultSearch,
                title = base.title,
                emptyTitle = base.emptyTitle,
                mailboxSearch = base.mailboxSearch,
                pills = base.pills,
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
                opening = states.requests.row.takeIf { feed === states.list.feed },
            ),
        messageActions = messageActions,
    )
}

/**
 * What a list page calls itself, by [title] and when it is empty by [emptyTitle], what its search
 * starts from and can widen to, and its row of [pills].
 */
internal class ListBase(
    val title: String,
    val emptyTitle: String,
    val defaultSearch: InboxSearch,
    val mailboxSearch: MailboxSearch,
    val pills: InboxPills,
) {
    companion object {
        /** A folder's page shows only the filter pills of [pills]: folder pills would leave the folder. */
        fun folder(
            name: String,
            pills: List<InboxPill>,
        ): ListBase =
            ListBase(
                title = name,
                emptyTitle = "$name is empty",
                defaultSearch = InboxSearch(mailboxes = emptySet()),
                mailboxSearch = MailboxSearch(),
                pills = InboxPills(pills.filterIsInstance<InboxPill.Filter>()),
            )
    }
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
    val folders =
        states.folders.all.accounts
            .inScope(states.mailScope)
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
