package com.otusfoss.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.mail.core.messages.domain.inScope
import com.otusfoss.mail.feature.folders.presentation.components.rememberFolderNudge
import com.otusfoss.mail.feature.inbox.presentation.InboxContext
import com.otusfoss.mail.feature.inbox.presentation.InboxFeedState
import com.otusfoss.mail.feature.inbox.presentation.ListBase
import com.otusfoss.mail.feature.inbox.presentation.OpenRequest
import com.otusfoss.mail.feature.inbox.presentation.QuickJumpTarget
import com.otusfoss.mail.feature.inbox.presentation.QuickViewContext
import com.otusfoss.mail.feature.inbox.presentation.screens.InboxScreen
import com.otusfoss.mail.feature.inbox.presentation.status
import com.otusfoss.mail.feature.sidebar.presentation.sidebarMailboxes

/**
 * A list of [feed]'s mail on [page], the Inbox and every folder page alike: [base] names the list
 * and says what its search starts from, and every page gets the same Read view, filters, quick
 * view, and scope switcher. [opening] is the row a tapped notification asks it to open. Only a
 * page whose [base] has categories sorts its mail into pills and offers them in the Read view.
 */
@Composable
internal fun ListPage(
    page: MailPage,
    feed: InboxFeedState,
    base: ListBase,
    states: MailStates,
    dateFormatter: DateFormatter,
    navigation: MailNavigation,
    opening: OpenRequest? = null,
) {
    val labeled = rememberLabeled(states, feed)
    val filters = rememberPageFilters(states, page, base, feed)
    val rowLook = rememberRowLook(states)
    val messageActions = rememberMessageActions(states, feed, navigation)
    InboxScreen(
        labeled,
        dateFormatter,
        navigation.openSidebar,
        feed.status(connected = states.accounts.accounts.isNotEmpty()),
        onRefresh = {
            states.compose.fileScheduled()
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
                onOpenDraft = rememberDraftOpener(states, navigation, page),
                filters = filters,
                filterOptions = rememberFilterOptions(states, base, filters.applied),
                rowLook = rowLook,
                readView =
                    rememberReadView(
                        states,
                        feed,
                        navigation,
                        page,
                        messageActions,
                        rowLook.own,
                        base.categories != null,
                    ),
                nudge =
                    rememberFolderNudge(
                        states.folders.nudge,
                        states.folders.colors,
                        states.moveToasts.toasts,
                        labeled,
                    ),
                onLoadMore = feed::loadMore,
                scope = states.scopeSwitch(states.mailScope, states.chooseScope),
                quick = rememberQuickView(states, page, navigation),
                opening = opening,
                categories = base.categories,
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
    val folders =
        states.folders.all.accounts
            .inScope(states.mailScope)
    val pinned =
        remember(folders, behavior.list.inbox, behavior.sidebar.layout, page) {
            sidebarMailboxes(folders, behavior.list.inbox, behavior.sidebar.layout).filterNot { it.pin == page.pin }
        }
    return QuickViewContext(
        behavior.list.quickView,
        pinned.map { mailbox ->
            QuickJumpTarget(mailbox.title, mailbox.icon) { navigation.go(launchPage(mailbox.pin)) }
        },
    )
}
