package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.setValue
import com.otus.mail.feature.inbox.domain.InboxSearch

/**
 * Screen-owned input state. Search itself is portable domain logic. Closing search and clearing
 * filters return to [defaultSearch]: the Inbox scope, or everything on a folder's page.
 */
class InboxState(
    searching: Boolean = false,
    initialSearch: InboxSearch? = null,
    val defaultSearch: InboxSearch = InboxSearch(),
) {
    var searching by mutableStateOf(searching)
        private set
    var search by mutableStateOf(initialSearch ?: defaultSearch)
        private set

    /** The search differs from this page's default. */
    val filtered: Boolean
        get() = search != defaultSearch

    fun startSearch() {
        searching = true
    }

    fun closeSearch() {
        searching = false
        search = defaultSearch
    }

    fun update(value: InboxSearch) {
        search = value
    }

    fun reset() {
        search = defaultSearch
    }

    companion object {
        /** The default is not saved: the page that owns the state supplies it again. */
        fun saver(defaultSearch: InboxSearch = InboxSearch()): Saver<InboxState, Any> =
            mapSaver(
                save = { state ->
                    mapOf("searching" to state.searching, "search" to with(InboxSearchSaver) { save(state.search) })
                },
                restore = {
                    InboxState(
                        it["searching"] as Boolean,
                        requireNotNull(InboxSearchSaver.restore(requireNotNull(it["search"]))),
                        defaultSearch,
                    )
                },
            )
    }
}
