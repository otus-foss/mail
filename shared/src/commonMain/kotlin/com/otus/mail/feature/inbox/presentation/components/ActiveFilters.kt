package com.otus.mail.feature.inbox.presentation.components

import com.otus.dateformat.DateFormatter
import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.FolderLabel
import com.otus.mail.feature.inbox.presentation.key

/** One applied choice as a pill: its [label], and the filters without it. */
internal class ActiveFacet(
    val key: Any,
    val label: String,
    val remove: (InboxFilters) -> InboxFilters,
)

/** A page's [applied] filters for the pill row, which [onChange] replaces. */
class ActiveFilters(
    val applied: InboxFilters = InboxFilters(),
    val options: FilterOptions = FilterOptions(),
    val dateFormatter: DateFormatter = DateFormatter(),
    val onChange: (InboxFilters) -> Unit = {},
) {
    /** Each applied choice, in the panel's order; the sort order is not one. */
    internal val active: List<ActiveFacet>
        get() =
            buildList {
                applied.show.sortedBy { it.ordinal }.forEach { facet ->
                    add(ActiveFacet(facet, facet.label) { it.with(facet, false) })
                }
                applied.sentTo.forEach { address ->
                    add(ActiveFacet(address, address.address) { it.copy(sentTo = it.sentTo - address) })
                }
                applied.folders.forEach { ref ->
                    val label = options.labels[ref.key] ?: FolderLabel(options.folder(ref.key)?.name ?: "Folder")
                    add(ActiveFacet(ref, label.withIncluded(ref, options)) { it.withFolder(ref, false) })
                }
                applied.date.takeIf { it != DateFilter.Any }?.let { date ->
                    val label = (date as? DateFilter.Range)?.let { rangeLabel(it, dateFormatter) } ?: date.label
                    add(ActiveFacet(DATE_KEY, label) { it.copy(date = DateFilter.Any) })
                }
            }

    private companion object {
        const val DATE_KEY = "date"
    }
}
