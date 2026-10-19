package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.ui.focus.FocusRequester
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_account_circle
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

/** One applied choice as its [pill], and what a tap on it does: [tap] removes it. */
internal class ActiveFacet(
    val key: Any,
    val tap: () -> Unit,
    val pill: ActivePill,
)

/**
 * A page's [applied] filters for the pill row, which [onChange] replaces. [people] give the
 * chosen people their avatars. [button] focuses the filter button, which the panel gives focus
 * back to when it closes.
 */
class ActiveFilters(
    val applied: InboxFilters = InboxFilters(),
    val options: FilterOptions = FilterOptions(),
    val dateFormatter: DateFormatter = DateFormatter(),
    val onChange: (InboxFilters) -> Unit = {},
    val button: FocusRequester = FocusRequester(),
    private val people: FilterPeople = FilterPeople(),
) {
    /**
     * Each applied choice in the panel's order, ending with the sort order while it is not the
     * default. Tapping a pill removes its choice.
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
                if (applied.from.isNotEmpty()) {
                    add(removing(InboxFilters::from, fromPill(applied, people)) { it.copy(from = emptySet()) })
                }
                // The To pill stands for the whole recipient filter, so removing it also resets Received in.
                val noRecipients = { filters: InboxFilters ->
                    filters.copy(sentTo = emptySet(), to = emptySet(), receivedIn = RecipientField.Default)
                }
                when {
                    applied.sentTo.isNotEmpty() || applied.to.isNotEmpty() -> {
                        add(removing(InboxFilters::to, toPill(applied, options, people), noRecipients))
                    }

                    applied.receivedIn != RecipientField.Default -> {
                        val label = "In " + applied.receivedIn.sortedBy { it.ordinal }.joinToString(", ") { it.label }
                        val pill = symbolPill(label, Res.drawable.symbol_alternate_email)
                        add(removing(InboxFilters::to, pill, noRecipients))
                    }
                }
                applied.account?.let { id ->
                    // An account this page no longer offers still filters, so its pill stays to show it.
                    val pill =
                        options.account(id)?.let(::accountPill) ?: symbolPill(id, Res.drawable.symbol_account_circle)
                    add(removing(InboxFilters::account, pill) { it.copy(account = null) })
                }
                if (applied.folders.isNotEmpty()) {
                    val pill = foldersPill(applied.folders.toList(), options)
                    add(removing(InboxFilters::folders, pill) { it.copy(folders = emptySet()) })
                }
                if (applied.date != DateFilter.Any) {
                    val pill = symbolPill(dateValue(applied.date, dateFormatter), Res.drawable.symbol_calendar_today)
                    add(removing(InboxFilters::date, pill) { it.copy(date = DateFilter.Any) })
                }
                applied.types.sortedBy { it.ordinal }.forEach { facet ->
                    add(removing(facet, symbolPill(facet.label, facet.icon)) { it.with(facet, false) })
                }
                if (applied.size != SizeFilter.Any) {
                    val pill = symbolPill(applied.size.label, Res.drawable.symbol_straighten)
                    add(removing(InboxFilters::size, pill) { it.copy(size = SizeFilter.Any) })
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
}
