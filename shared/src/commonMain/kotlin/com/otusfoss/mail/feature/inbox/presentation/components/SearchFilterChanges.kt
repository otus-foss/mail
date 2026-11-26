package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.otusfoss.mail.core.messages.domain.QuickFacet
import com.otusfoss.mail.feature.inbox.domain.InboxFilters
import com.otusfoss.mail.feature.inbox.presentation.InboxFilterState
import com.otusfoss.mail.feature.inbox.presentation.InboxFiltersSaver

/**
 * Which of the page's filters a search changed — Has files, the date, and a folder page's
 * search-time folders — and what the page's filters were [before] its first change. Closing the
 * search sets those back with [undo], so a filter on before the search stays on however often it
 * is tapped; the filter panel's Show makes Has files and the date the page's with [keep], while a
 * folder page's search folders always return to their pre-search value when search closes.
 */
internal class SearchFilterChanges(
    private var before: InboxFilters? = null,
    private var files: Boolean = false,
    private var date: Boolean = false,
    private var resetFolders: Boolean = false,
) {
    /** Whether search has already seeded the folder page's default folder selection. */
    val foldersInitialized: Boolean
        get() = resetFolders

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

    /** Notes that search seeded a folder page's default folder selection from [applied]. */
    fun startFolderSearch(applied: InboxFilters) {
        if (resetFolders) return
        before = before ?: applied
        resetFolders = true
    }

    fun keep() {
        files = false
        date = false
        if (!resetFolders) before = null
    }

    fun undo(filters: InboxFilterState) {
        val start = before ?: return
        val applied = filters.applied
        val restored =
            applied
                .with(QuickFacet.Attachments, on = (if (files) start else applied).has(QuickFacet.Attachments))
                .copy(
                    date = (if (date) start else applied).date,
                    folders = (if (resetFolders) start else applied).folders,
                    includedBins = (if (resetFolders) start else applied).includedBins,
                )
        before = null
        files = false
        date = false
        resetFolders = false
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
                        changes.resetFolders,
                    )
                },
                restore = { values ->
                    SearchFilterChanges(
                        values[0]?.let(InboxFiltersSaver::restore),
                        values[1] as Boolean,
                        values[2] as Boolean,
                        values.getOrNull(3) as? Boolean ?: false,
                    )
                },
            )
    }
}
