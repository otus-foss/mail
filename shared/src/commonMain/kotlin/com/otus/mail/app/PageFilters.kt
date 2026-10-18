package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.mail.feature.folders.domain.subtreeIds
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.folderIds
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.FolderKey
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.InboxFilterState
import com.otus.mail.feature.inbox.presentation.InboxFiltersSaver
import com.otus.mail.feature.inbox.presentation.key

/**
 * Each page's applied filters, kept while the app runs so a page shows them again after the user
 * comes back, and the folders filtered by most recently, newest first.
 */
internal class PageFilters {
    private val applied = mutableMapOf<MailPage, InboxFilters>()

    var recentFolders: List<FolderKey> = emptyList()
        private set

    fun of(page: MailPage): InboxFilters = applied[page] ?: InboxFilters()

    fun keep(
        page: MailPage,
        filters: InboxFilters,
    ) {
        applied[page] = filters
        recentFolders = (filters.folders.map { it.key } + recentFolders).distinct().take(RECENT_FOLDERS)
    }

    private companion object {
        const val RECENT_FOLDERS = 12
    }
}

/**
 * What the server needs to run [filters]: chosen folders with their subfolders, each account's Read
 * later folder, the user's addresses, and the account "Show mail from" picks. Default filters need
 * none of it, so they never load the list again.
 */
internal fun MailStates.mailQuery(filters: InboxFilters): MailQuery {
    if (filters.isDefault) return MailQuery()
    val accounts = folders.accounts
    val descendants = { login: String, id: String ->
        val account = accounts.firstOrNull { it.accountId == login }
        account?.folder(id)?.let { subtreeIds(account.folders, it) - id }.orEmpty()
    }
    return MailQuery(
        filters = filters,
        folderIds = folderIds(filters.folders, descendants),
        skipIds = folderIds(filters.skipFolders, descendants),
        readLaterIds = accounts.mapNotNull { account -> account.readLaterId?.let { account.accountId to it } }.toMap(),
        addresses = accountAddresses().associate { it.accountId to it.addresses.toSet() },
        logins = mailScope.accountId?.let(::setOf),
    )
}

/**
 * [page]'s filters, which load [feed] again whenever they change and count the panel's draft in
 * the mail [feed] shows.
 */
@Composable
internal fun rememberPageFilters(
    states: MailStates,
    page: MailPage,
    feed: InboxFeedState,
): InboxFilterState {
    val scope = rememberCoroutineScope()
    // Saved too, so the filters outlast an activity recreation, which starts new state holders.
    var saved by rememberSaveable(page, stateSaver = InboxFiltersSaver) { mutableStateOf(states.pageFilters.of(page)) }
    val filters =
        remember(states, page, feed) {
            InboxFilterState(
                saved,
                scope,
                counter = { states.countMail(feed.mailScope, states.mailQuery(it)) },
                onApplied = {
                    states.pageFilters.keep(page, it)
                    saved = it
                },
            )
        }
    LaunchedEffect(filters.applied, states.folders.accounts, states.mailScope) {
        feed.query = states.mailQuery(filters.applied)
    }
    return filters
}

/** [page]'s filter choices, built again when folders, accounts, colors, pins, or [applied] filters change. */
@Composable
internal fun rememberFilterOptions(
    states: MailStates,
    page: MailPage,
    applied: InboxFilters,
): FilterOptions =
    remember(
        page,
        applied,
        states.folders.accounts,
        states.accounts.accounts,
        states.mailScope,
        states.folderColors.colors,
        states.behavior.behavior.sidebar,
        states.behavior.behavior.filters,
    ) { states.filterOptions(page) }
