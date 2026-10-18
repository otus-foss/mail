package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.feature.inbox.domain.FoundPerson
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.InboxStorageException
import com.otus.mail.feature.inbox.domain.PeopleField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How many messages the filter panel's draft would show. */
sealed interface FilterCount {
    data object Counting : FilterCount

    /** The source could not count them, so the button names no number. */
    data object Unknown : FilterCount

    data class Known(
        val messages: Long,
    ) : FilterCount
}

/** A page of the filter panel: the main page, or a page one of its rows or lines opens. */
enum class FilterPage { Main, From, To, Account, Folders, Date, Metadata }

/**
 * A page's [applied] filters, which the quick pills change at once, and the filter panel's
 * [draft] while it is open, which every [page] of the panel edits. Opening copies the applied
 * filters into the draft on the main page; Show applies the draft, and closing drops it. Every
 * draft change counts its messages again with [counter] in [scope], [COUNT_DELAY_MILLIS] after
 * the last change, cancelling a count still running; [onOpen] starts each panel session's counts.
 * [onApplied] hears every new applied value, so the page can load it and keep it. [senders] and
 * [recipients] search people for the From and To pages with [findPeople].
 */
class InboxFilterState(
    applied: InboxFilters = InboxFilters(),
    private val scope: CoroutineScope? = null,
    private val counter: suspend (InboxFilters) -> Long? = { null },
    private val onApplied: (InboxFilters) -> Unit = {},
    private val onOpen: () -> Unit = {},
    findPeople: suspend (PeopleField, String) -> List<FoundPerson> = { _, _ -> emptyList() },
) {
    var applied by mutableStateOf(applied)
        private set

    var draft by mutableStateOf<InboxFilters?>(null)
        private set

    var count by mutableStateOf<FilterCount>(FilterCount.Unknown)
        private set

    /** The panel page on screen; it stays while the panel closes, and opening starts on the main page. */
    var page by mutableStateOf(FilterPage.Main)
        private set

    val senders = FilterPeopleSearch(PeopleField.From, scope, findPeople)

    val recipients = FilterPeopleSearch(PeopleField.To, scope, findPeople)

    val isOpen: Boolean
        get() = draft != null

    private var counting: Job? = null

    fun open() {
        page = FilterPage.Main
        onOpen()
        draft = applied
        recount()
    }

    /** Closes the panel and forgets its changes. */
    fun close() {
        counting?.cancel()
        draft = null
        senders.clear()
        recipients.clear()
    }

    /** Shows [page] of the open panel, which edits the same draft. */
    fun openPage(page: FilterPage) {
        if (isOpen) this.page = page
    }

    /** Back: a sub-page returns to the main page, and the main page closes the panel, dropping the draft. */
    fun back() {
        if (page == FilterPage.Main) close() else page = FilterPage.Main
    }

    /** Resets the draft to no filters, on whichever page is open. */
    fun reset() = edit { InboxFilters() }

    fun edit(transform: (InboxFilters) -> InboxFilters) {
        val current = draft ?: return
        val next = transform(current)
        if (next == current) return
        draft = next
        recount()
    }

    /** Applies the draft and closes the panel. */
    fun show() {
        draft?.let(::update)
        close()
    }

    /** Applies [filters] at once, as a quick pill does. */
    fun update(filters: InboxFilters) {
        if (filters == applied) return
        applied = filters
        onApplied(filters)
    }

    private fun recount() {
        val filters = draft ?: return
        counting?.cancel()
        val launcher = scope
        if (launcher == null) {
            count = FilterCount.Unknown
            return
        }
        count = FilterCount.Counting
        counting =
            launcher.launch {
                delay(COUNT_DELAY_MILLIS)
                val total =
                    try {
                        counter(filters)
                    } catch (expected: InboxStorageException) {
                        null
                    }
                count = total?.let(FilterCount::Known) ?: FilterCount.Unknown
            }
    }

    companion object {
        /** Typing quickly through choices counts only the last one. */
        const val COUNT_DELAY_MILLIS = 250L
    }
}

/** The panel's Show button for this count. */
val FilterCount.showLabel: String
    get() =
        when {
            this !is FilterCount.Known -> "Show conversations"
            messages == 0L -> "No conversations"
            messages == 1L -> "Show 1 conversation"
            else -> "Show $messages conversations"
        }
