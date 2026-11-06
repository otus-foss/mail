package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.otus.mail.core.messages.domain.QuickFacet
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.presentation.InboxFilterState
import com.otus.mail.feature.inbox.presentation.InboxFiltersSaver

/**
 * Which of the page's filters a search changed, Has files and the date, and what the page's
 * filters were [before] its first change. Closing the search sets those back with [undo], so a
 * filter on before the search stays on however often it is tapped; the filter panel's Show makes
 * them the page's with [keep].
 */
internal class SearchFilterChanges(
    private var before: InboxFilters? = null,
    private var files: Boolean = false,
    private var date: Boolean = false,
) {
    /** Notes a change to Has files, made while [applied] are the page's filters. */
    fun files(applied: InboxFilters) {
        before = before ?: applied
        files = true
    }

    /** Notes a change to the date, made while [applied] are the page's filters. */
    fun recent(applied: InboxFilters) {
        before = before ?: applied
        date = true
    }

    fun keep() {
        before = null
        files = false
        date = false
    }

    fun undo(filters: InboxFilterState) {
        val start = before ?: return
        val applied = filters.applied
        val restored =
            applied
                .with(QuickFacet.Attachments, on = (if (files) start else applied).has(QuickFacet.Attachments))
                .copy(date = (if (date) start else applied).date)
        keep()
        if (restored != applied) filters.update(restored)
    }

    companion object {
        /** Keeps the changes through activity recreation, so closing the search still sets them back. */
        val saver: Saver<SearchFilterChanges, Any> =
            listSaver(
                save = { changes ->
                    listOf(
                        changes.before?.let { with(InboxFiltersSaver) { save(it) } },
                        changes.files,
                        changes.date,
                    )
                },
                restore = { (before, files, date) ->
                    SearchFilterChanges(before?.let(InboxFiltersSaver::restore), files as Boolean, date as Boolean)
                },
            )
    }
}
