package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.setValue
import com.otus.mail.feature.inbox.domain.InboxSearch

/** Screen-owned input state. Search itself is portable domain logic. */
class InboxState(
    searching: Boolean = false,
    initialSearch: InboxSearch = InboxSearch(),
) {
    var searching by mutableStateOf(searching)
        private set
    var search by mutableStateOf(initialSearch)
        private set

    fun startSearch() {
        searching = true
    }

    fun closeSearch() {
        searching = false
        search = InboxSearch()
    }

    fun update(value: InboxSearch) {
        search = value
    }

    companion object {
        val Saver =
            mapSaver(
                save = { state ->
                    mapOf("searching" to state.searching, "search" to with(InboxSearchSaver) { save(state.search) })
                },
                restore = {
                    InboxState(
                        it["searching"] as Boolean,
                        requireNotNull(InboxSearchSaver.restore(requireNotNull(it["search"]))),
                    )
                },
            )
    }
}
