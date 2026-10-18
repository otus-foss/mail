package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.ui.focus.FocusRequester
import com.otus.dateformat.DateFormatter
import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.FolderRef
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.SizeFilter
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.FolderLabel
import com.otus.mail.feature.inbox.presentation.key

/** One applied choice as a pill: its [label], and the filters without it. */
internal class ActiveFacet(
    val key: Any,
    val label: String,
    val remove: (InboxFilters) -> InboxFilters,
)

/**
 * A page's [applied] filters for the pill row, which [onChange] replaces. [button] focuses the
 * filter button, which the panel gives focus back to when it closes.
 */
class ActiveFilters(
    val applied: InboxFilters = InboxFilters(),
    val options: FilterOptions = FilterOptions(),
    val dateFormatter: DateFormatter = DateFormatter(),
    val onChange: (InboxFilters) -> Unit = {},
    val button: FocusRequester = FocusRequester(),
) {
    /** Each applied choice, in the panel's order; the sort order is not one. */
    internal val active: List<ActiveFacet>
        get() =
            buildList {
                applied.show.sortedBy { it.ordinal }.forEach { facet ->
                    add(ActiveFacet(facet, facet.label) { it.with(facet, false) })
                }
                applied.from.forEach { address ->
                    add(ActiveFacet("from" to address, "From $address") { it.copy(from = it.from - address) })
                }
                applied.accounts.forEach { id ->
                    val name = options.account(id)?.name ?: id
                    add(ActiveFacet("account" to id, name) { it.copy(accounts = it.accounts - id) })
                }
                applied.sentTo.forEach { address ->
                    add(ActiveFacet(address, address.address) { it.copy(sentTo = it.sentTo - address) })
                }
                applied.receivedAs.sortedBy { it.ordinal }.forEach { field ->
                    add(ActiveFacet(field, "As ${field.label}") { it.copy(receivedAs = it.receivedAs - field) })
                }
                applied.folders.forEach { ref ->
                    add(ActiveFacet(ref, folderName(ref).withIncluded(ref, options)) { it.withFolder(ref, false) })
                }
                applied.skipFolders.forEach { ref ->
                    add(ActiveFacet("skip" to ref, "Not in ${folderName(ref).name}") { it.withSkipped(ref, false) })
                }
                applied.date.takeIf { it != DateFilter.Any }?.let { date ->
                    val label = (date as? DateFilter.Range)?.let { rangeLabel(it, dateFormatter) } ?: date.label
                    add(ActiveFacet(DATE_KEY, label) { it.copy(date = DateFilter.Any) })
                }
                applied.size.takeIf { it != SizeFilter.Any }?.let { size ->
                    add(ActiveFacet(SIZE_KEY, size.label) { it.copy(size = SizeFilter.Any) })
                }
            }

    private fun folderName(ref: FolderRef): FolderLabel =
        options.labels[ref.key] ?: FolderLabel(options.folder(ref.key)?.name ?: "Folder")

    private companion object {
        const val DATE_KEY = "date"
        const val SIZE_KEY = "size"
    }
}
