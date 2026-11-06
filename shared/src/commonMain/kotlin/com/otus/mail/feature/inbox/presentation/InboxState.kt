package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.setValue
import com.otus.mail.core.settings.domain.InboxPill
import com.otus.mail.feature.inbox.domain.InboxSearch

/**
 * Screen-owned input state. Search itself is portable domain logic. Closing search and clearing
 * filters return to [defaultSearch]: the Inbox scope, or everything on a folder's page. A
 * [folderPill] shows its folders' mail in place of the page's own, and searches and filters apply
 * to that mail until the pill is turned off.
 */
class InboxState(
    searching: Boolean = false,
    initialSearch: InboxSearch? = null,
    val defaultSearch: InboxSearch = InboxSearch(),
    folderPill: InboxPill.FolderPill? = null,
    showingResults: Boolean = false,
) {
    var searching by mutableStateOf(searching)
        private set
    var search by mutableStateOf(initialSearch ?: defaultSearch)
        private set
    var folderPill by mutableStateOf(folderPill)
        private set

    /**
     * Whether the search shows its full results in the list. Until then the control panel grows
     * into the search surface, with ways to narrow it and the first hits.
     */
    var showingResults by mutableStateOf(showingResults)
        private set

    /** Whether the control panel is grown into the search surface over the list. */
    val searchSurfaceOpen: Boolean
        get() = searching && !showingResults

    /** Whether the search's full results show in the list, under the search field in the panel. */
    val resultsShown: Boolean
        get() = searching && showingResults

    /** What the list matches. A folder pill's mail is all loaded for it, so the page's mailboxes do not apply. */
    val matching: InboxSearch
        get() = if (folderPill == null) search else search.copy(mailboxes = emptySet())

    /** The search differs from this page's default. */
    val filtered: Boolean
        get() = search != defaultSearch

    fun startSearch() {
        searching = true
        showingResults = false
    }

    fun closeSearch() {
        searching = false
        showingResults = false
        search = defaultSearch
    }

    fun showResults() {
        showingResults = true
    }

    /** Back from the full results to the search surface, to change the search. */
    fun editSearch() {
        showingResults = false
    }

    fun update(value: InboxSearch) {
        search = value
    }

    fun reset() {
        search = defaultSearch
    }

    /** Shows [pill]'s folders, or the page's own mail again when it is null. */
    fun showFolder(pill: InboxPill.FolderPill?) {
        folderPill = pill
    }

    companion object {
        /** The default is not saved: the page that owns the state supplies it again. */
        fun saver(defaultSearch: InboxSearch = InboxSearch()): Saver<InboxState, Any> =
            mapSaver(
                save = { state ->
                    mapOf(
                        "searching" to state.searching,
                        "search" to with(InboxSearchSaver) { save(state.search) },
                        "folderPill" to state.folderPill?.storageValue,
                        "showingResults" to state.showingResults,
                    )
                },
                restore = {
                    InboxState(
                        it["searching"] as Boolean,
                        requireNotNull(InboxSearchSaver.restore(requireNotNull(it["search"]))),
                        defaultSearch,
                        (it["folderPill"] as String?)?.let(InboxPill::fromStorageValue) as? InboxPill.FolderPill,
                        it["showingResults"] as Boolean,
                    )
                },
            )
    }
}
