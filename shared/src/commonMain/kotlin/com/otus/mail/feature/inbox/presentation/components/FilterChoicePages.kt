package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.DateRangeCalendar
import com.otus.designsystem.components.FilterPickRow
import com.otus.designsystem.components.FilterRowCard
import com.otus.designsystem.components.FilterSection
import com.otus.designsystem.components.PersonAvatar
import com.otus.designsystem.components.PickMark
import com.otus.designsystem.components.SegmentedToggle
import com.otus.designsystem.elements.FilterChip
import com.otus.designsystem.elements.FilterChipRole
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_all_inbox
import com.otus.designsystem.theme.FilterTokens
import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.SizeFilter
import com.otus.mail.feature.inbox.domain.TypeFacet
import com.otus.mail.feature.inbox.presentation.FilterAccount
import com.otus.mail.feature.inbox.presentation.FilterOptions
import kotlinx.datetime.LocalDate

/** The Account page: all accounts, or one login, each with the addresses it receives. */
@Composable
internal fun AccountPage(
    draft: InboxFilters,
    options: FilterOptions,
    onEdit: FilterEdit,
) {
    FilterPageColumn {
        FilterRowCard(
            buildList {
                add {
                    FilterPickRow(
                        "All accounts",
                        "${options.accounts.size} accounts · ${options.addresses.size} addresses",
                        PickMark(draft.account == null),
                        { onEdit { it.copy(account = null) } },
                        leading = { Symbol(Res.drawable.symbol_all_inbox) },
                    )
                }
                options.accounts.forEach { account -> add { AccountRow(account, draft, options, onEdit) } }
            },
        )
    }
}

@Composable
private fun AccountRow(
    account: FilterAccount,
    draft: InboxFilters,
    options: FilterOptions,
    onEdit: FilterEdit,
) {
    val addresses = options.addresses.filter { it.address.accountId == account.id }.map { it.address.address }
    val title = addresses.firstOrNull() ?: account.name
    val others = addresses.drop(1).takeIf { it.isNotEmpty() }?.let { "also ${it.joinToString()}" }
    val subtitle = listOfNotNull(account.name.takeIf { it != title }, others).joinToString(" · ").ifEmpty { null }
    FilterPickRow(
        title,
        subtitle,
        PickMark(draft.account == account.id),
        { onEdit { it.copy(account = account.id) } },
        leading = { PersonAvatar(account.avatar, FilterTokens.pickAvatarSize) },
    )
}

/** The Date page: presets, or Custom, which shows a range calendar on the page. */
@Composable
internal fun DatePage(
    date: DateFilter,
    onEdit: FilterEdit,
) {
    val range = date as? DateFilter.Range
    var custom by rememberSaveable { mutableStateOf(range != null) }
    var start by rememberSaveable { mutableStateOf(range?.from?.toString()) }
    var end by rememberSaveable { mutableStateOf(range?.to?.toString()) }
    // Reset, or a preset after a range, also forgets the range the calendar shows.
    LaunchedEffect(date) {
        if (date !is DateFilter.Range && start != null) {
            custom = false
            start = null
            end = null
        }
    }
    FilterPageColumn {
        FilterSection("Received") {
            DATE_PRESETS.forEach { preset ->
                FilterChip(preset.label, !custom && date == preset, {
                    custom = false
                    onEdit { it.copy(date = preset) }
                }, role = FilterChipRole.Radio)
            }
            // Custom drops the preset at once, so the count and Show match the empty calendar.
            FilterChip("Custom", custom, {
                if (!custom) {
                    custom = true
                    onEdit { it.copy(date = DateFilter.Any) }
                }
            }, role = FilterChipRole.Radio)
        }
        if (custom) {
            DateRangeCalendar(start, end, { first, last ->
                start = first
                end = last
                val picked = first?.let { DateFilter.Range(LocalDate.parse(it), LocalDate.parse(last ?: it)) }
                onEdit { it.copy(date = picked ?: DateFilter.Any) }
            }, Modifier.height(FilterTokens.rangeCalendarHeight))
        }
    }
}

/** The Metadata page: the mail types and the size threshold. */
@Composable
internal fun MetadataPage(
    draft: InboxFilters,
    onEdit: FilterEdit,
) {
    FilterPageColumn {
        FilterSection("Type") {
            TypeFacet.entries.forEach { facet ->
                val on = draft.has(facet)
                FilterChip(facet.label, on, { onEdit { it.with(facet, !on) } }) {
                    Symbol(facet.icon, size = FilterTokens.chipIconSize)
                }
            }
        }
        FilterSection("Size") {
            SegmentedToggle(
                SizeFilter.entries.map { it.label },
                SizeFilter.entries.indexOf(draft.size),
                { index -> onEdit { it.copy(size = SizeFilter.entries[index]) } },
                height = FilterTokens.segmentHeight,
            )
        }
    }
}

private val DATE_PRESETS =
    listOf(DateFilter.Any, DateFilter.Today, DateFilter.Last7, DateFilter.Last30, DateFilter.ThisYear)
