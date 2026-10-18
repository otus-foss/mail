package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.InboxStorageException
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

/**
 * A page's [applied] filters, which the quick pills change at once, and the filter panel's
 * [draft] while it is open. Opening copies the applied filters into the draft; Show applies the
 * draft, and closing drops it. Every draft change counts its messages again with [counter] in
 * [scope], [COUNT_DELAY_MILLIS] after the last change, cancelling a count still running.
 * [onApplied] hears every new applied value, so the page can load it and keep it.
 */
class InboxFilterState(
    applied: InboxFilters = InboxFilters(),
    private val scope: CoroutineScope? = null,
    private val counter: suspend (InboxFilters) -> Long? = { null },
    private val onApplied: (InboxFilters) -> Unit = {},
) {
    var applied by mutableStateOf(applied)
        private set

    var draft by mutableStateOf<InboxFilters?>(null)
        private set

    var count by mutableStateOf<FilterCount>(FilterCount.Unknown)
        private set

    val isOpen: Boolean
        get() = draft != null

    private var counting: Job? = null

    fun open() {
        draft = applied
        recount()
    }

    /** Closes the panel and forgets its changes. */
    fun close() {
        counting?.cancel()
        draft = null
    }

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
