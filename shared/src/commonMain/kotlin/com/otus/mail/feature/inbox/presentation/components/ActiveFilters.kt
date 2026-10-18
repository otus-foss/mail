package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.ui.focus.FocusRequester
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_alternate_email
import com.otus.designsystem.resources.symbol_calendar_today
import com.otus.designsystem.resources.symbol_straighten
import com.otus.designsystem.resources.symbol_swap_vert
import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.FilterSort
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.ReadStatus
import com.otus.mail.feature.inbox.domain.RecipientField
import com.otus.mail.feature.inbox.domain.SizeFilter
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.FilterPage

/** One applied choice as its [pill], and what a tap on it does: [tap] removes it or opens its page. */
internal class ActiveFacet(
    val key: Any,
    val tap: () -> Unit,
    val pill: ActivePill,
)

/**
 * A page's [applied] filters for the pill row, which [onChange] replaces and [onOpen] opens the
 * filter panel on a page of. [people] give the chosen people their avatars. [button] focuses the
 * filter button, which the panel gives focus back to when it closes.
 */
class ActiveFilters(
    val applied: InboxFilters = InboxFilters(),
    val options: FilterOptions = FilterOptions(),
    val dateFormatter: DateFormatter = DateFormatter(),
    val onChange: (InboxFilters) -> Unit = {},
    val button: FocusRequester = FocusRequester(),
    private val onOpen: (FilterPage) -> Unit = {},
    private val people: FilterPeople = FilterPeople(),
) {
    /**
     * Each applied choice in the panel's order, ending with the sort order while it is not the
     * default. On/off choices go away when tapped; choices with a page open it.
     */
    internal val active: List<ActiveFacet>
        get() =
            buildList {
                applied.status.takeIf { it != ReadStatus.All }?.let { status ->
                    add(removing(status, symbolPill(status.label, status.icon)) { it.copy(status = ReadStatus.All) })
                }
                applied.quick.sortedBy { it.ordinal }.forEach { facet ->
                    add(removing(facet, symbolPill(facet.label, facet.icon)) { it.with(facet, false) })
                }
                if (applied.from.isNotEmpty()) add(opening(FilterPage.From, fromPill(applied, people)))
                when {
                    applied.sentTo.isNotEmpty() || applied.to.isNotEmpty() -> {
                        add(opening(FilterPage.To, toPill(applied, options, people)))
                    }

                    applied.receivedIn != RecipientField.Default -> {
                        val label = "In " + applied.receivedIn.sortedBy { it.ordinal }.joinToString(", ") { it.label }
                        add(opening(FilterPage.To, symbolPill(label, Res.drawable.symbol_alternate_email)))
                    }
                }
                applied.account?.let(options::account)?.let { add(opening(FilterPage.Account, accountPill(it))) }
                if (applied.folders.isNotEmpty()) {
                    add(opening(FilterPage.Folders, foldersPill(applied.folders.toList(), options)))
                }
                if (applied.date != DateFilter.Any) {
                    val pill = symbolPill(dateValue(applied.date, dateFormatter), Res.drawable.symbol_calendar_today)
                    add(opening(FilterPage.Date, pill))
                }
                applied.types.sortedBy { it.ordinal }.forEach { facet ->
                    add(opening(FilterPage.Metadata, symbolPill(facet.label, facet.icon), key = facet))
                }
                if (applied.size != SizeFilter.Any) {
                    val pill = symbolPill(applied.size.label, Res.drawable.symbol_straighten)
                    add(opening(FilterPage.Metadata, pill, key = SizeFilter::class))
                }
                if (applied.sort != FilterSort.NewestFirst) {
                    val pill = symbolPill("Oldest first", Res.drawable.symbol_swap_vert)
                    add(removing(FilterSort.OldestFirst, pill) { it.copy(sort = FilterSort.NewestFirst) })
                }
            }

    private fun removing(
        key: Any,
        pill: ActivePill,
        remove: (InboxFilters) -> InboxFilters,
    ) = ActiveFacet(key, { onChange(remove(applied)) }, pill)

    /** A pill that opens [page]; the Metadata page has several, each with its own [key]. */
    private fun opening(
        page: FilterPage,
        pill: ActivePill,
        key: Any = page,
    ) = ActiveFacet(key, { onOpen(page) }, pill)
}
