package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.designsystem.theme.colors.FolderColor
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.InboxFilterState
import com.otus.mail.feature.inbox.presentation.InboxFiltersSaver
import com.otus.mail.feature.inbox.presentation.ListBase
import com.otus.mail.feature.inbox.presentation.filterOptions
import com.otus.mail.feature.inbox.presentation.mailQuery

/** Each page's applied filters, kept while the app runs so a page shows them again after the user comes back. */
internal class PageFilters {
    private val applied = mutableMapOf<MailPage, InboxFilters>()

    /** The filters [page] last applied, or else [starting]. */
    fun of(
        page: MailPage,
        starting: InboxFilters,
    ): InboxFilters = applied[page] ?: starting

    fun keep(
        page: MailPage,
        filters: InboxFilters,
    ) {
        applied[page] = filters
    }
}

private fun MailStates.mailQuery(filters: InboxFilters): MailQuery =
    mailQuery(filters, folders.all.accounts, accountAddresses(), mailScope.accountId?.let(::setOf))

/**
 * [page]'s filters, which start as [base] says, load [feed] again whenever they change, and count
 * the panel's draft in the mail [feed] shows.
 */
@Composable
internal fun rememberPageFilters(
    states: MailStates,
    page: MailPage,
    base: ListBase,
    feed: InboxFeedState,
): InboxFilterState {
    val scope = rememberCoroutineScope()
    // Saved too, so the filters outlast an activity recreation, which starts new state holders.
    val pageFilters = states.list.pageFilters
    var saved by rememberSaveable(page, stateSaver = InboxFiltersSaver) {
        mutableStateOf(pageFilters.of(page, base.startingFilters))
    }
    val filters =
        remember(states, page, feed) {
            InboxFilterState(
                saved,
                scope,
                counter = { states.list.filterLookups.count(feed.mailScope, states.mailQuery(it)) },
                onApplied = {
                    pageFilters.keep(page, it)
                    saved = it
                },
                findPeople = states.list.filterLookups::findPeople,
            )
        }
    LaunchedEffect(filters.applied, states.folders.all.accounts, states.mailScope) {
        feed.query = states.mailQuery(filters.applied)
    }
    return filters
}

/**
 * The filter choices of a page [base] names, built again when folders, accounts, colors, pins, or
 * [applied] filters change.
 */
@Composable
internal fun rememberFilterOptions(
    states: MailStates,
    base: ListBase,
    applied: InboxFilters,
): FilterOptions =
    remember(
        base.byFolder,
        applied,
        states.folders.all.accounts,
        states.accounts.accounts,
        states.mailScope,
        states.folders.colors.colors,
        states.behavior.behavior.sidebar.layout,
        states.behavior.behavior.list.filters,
    ) {
        filterOptions(
            states.folders.all.accounts,
            states.accountAddresses(),
            states.mailScope,
            { FolderColor.named(states.folders.colors.colorOf(it)) },
            base.byFolder,
            states.behavior.behavior.list.filters,
        )
    }
