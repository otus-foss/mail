package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.otusfoss.mail.core.messages.domain.QuickFacet
import com.otusfoss.mail.feature.inbox.domain.InboxFilters
import com.otusfoss.mail.feature.inbox.presentation.InboxFilterState
import com.otusfoss.mail.feature.inbox.presentation.InboxFiltersSaver

/**
 * Which of the page's filters a search changed, Has files and the date, and what the page's
 * filters were [before] its first change. Closing the search sets those back with [undo], so a
 * filter on before the search stays on however often it is tapped; the filter panel's Show makes
 * them the page's with [keep]. The folders, Spam and Trash a search picks always go back to their
 * value at [startSearch], because they choose where the search looks, not what the page shows.
 */
internal class SearchFilterChanges(
    private var before: InboxFilters? = null,
    private var files: Boolean = false,
    private var date: Boolean = false,
    private var searchStart: InboxFilters? = null,
) {
    /** Notes the page's [applied] filters as a search starts. */
    fun startSearch(applied: InboxFilters) {
        searchStart = searchStart ?: applied
    }

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
        val applied = filters.applied
        val restored = foldersUndone(changesUndone(applied))
        keep()
        searchStart = null
        if (restored != applied) filters.update(restored)
    }

    private fun changesUndone(applied: InboxFilters): InboxFilters {
        val start = before ?: return applied
        return applied
            .with(QuickFacet.Attachments, on = (if (files) start else applied).has(QuickFacet.Attachments))
            .copy(date = (if (date) start else applied).date)
    }

    private fun foldersUndone(applied: InboxFilters): InboxFilters =
        searchStart?.let {
            applied.copy(
                folders = it.folders,
                includedBins = it.includedBins,
                allFolders = it.allFolders,
            )
        }
            ?: applied

    companion object {
        /** Keeps the changes through activity recreation, so closing the search still sets them back. */
        val saver: Saver<SearchFilterChanges, Any> =
            listSaver(
                save = { changes ->
                    listOf(
                        changes.before?.let { with(InboxFiltersSaver) { save(it) } },
                        changes.files,
                        changes.date,
                        changes.searchStart?.let { with(InboxFiltersSaver) { save(it) } },
                    )
                },
                restore = { values ->
                    SearchFilterChanges(
                        values[0]?.let(InboxFiltersSaver::restore),
                        values[1] as Boolean,
                        values[2] as Boolean,
                        values[3]?.let(InboxFiltersSaver::restore),
                    )
                },
            )
    }
}
