package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_account_circle
import com.otus.designsystem.resources.symbol_all_inbox
import com.otus.designsystem.resources.symbol_calendar_today
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.controls.elements.UiAction
import com.otus.designsystem.shared.dates.components.DateRangePicker
import com.otus.designsystem.shared.filters.components.FilterRowCard
import com.otus.designsystem.shared.filters.components.FilterValueRow
import com.otus.designsystem.shared.overlays.components.ListSheet
import com.otus.designsystem.shared.people.components.PersonAvatar
import com.otus.designsystem.shared.selection.components.Dropdown
import com.otus.designsystem.shared.selection.components.PickMark
import com.otus.designsystem.shared.selection.components.PickRow
import com.otus.designsystem.shared.selection.components.PickStyle
import com.otus.designsystem.theme.tokens.editing.ComposeTokens
import com.otus.mail.core.messages.domain.DateFilter
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.presentation.FilterAccount
import com.otus.mail.feature.inbox.presentation.FilterOptions
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.DrawableResource

/**
 * The Account, Folders, and Date rows on one card. Account and Date open a list under their row,
 * and Folders opens a sheet with the folder tree; each choice changes the draft at once.
 */
@Composable
internal fun PopupRows(
    draft: InboxFilters,
    options: FilterOptions,
    dateFormatter: DateFormatter,
    onEdit: FilterEdit,
) {
    val rows =
        buildList<@Composable () -> Unit> {
            if (options.offersAccount) {
                add {
                    PopupRow(Res.drawable.symbol_account_circle, "Account", accountValue(draft, options)) { close ->
                        AccountChoices(draft, options, onEdit, close)
                    }
                }
            }
            if (options.folders) {
                add { FolderPopup(draft, options, onEdit) }
            }
            add { DatePopup(draft, dateFormatter, onEdit) }
        }
    FilterRowCard(rows)
}

@Composable
private fun FolderPopup(
    draft: InboxFilters,
    options: FilterOptions,
    onEdit: FilterEdit,
) {
    var open by remember { mutableStateOf(false) }
    ValueRow(Res.drawable.symbol_folder, "Folders", foldersValue(draft, options)) { open = true }
    if (open) FolderPickerSheet(draft, options, onEdit) { open = false }
}

@Composable
private fun DatePopup(
    draft: InboxFilters,
    dateFormatter: DateFormatter,
    onEdit: FilterEdit,
) {
    val date = FilterRowValue(dateValue(draft.date, dateFormatter), draft.date != DateFilter.Any)
    var custom by remember { mutableStateOf(false) }
    PopupRow(Res.drawable.symbol_calendar_today, "Date", date) { close ->
        DateChoices(draft.date, onEdit, close) { custom = true }
    }
    if (custom) DateRangeSheet(draft.date, dateFormatter, onEdit) { custom = false }
}

/** A row whose tap opens a list under it; [choices] get a way to close it. */
@Composable
private fun PopupRow(
    icon: DrawableResource,
    title: String,
    value: FilterRowValue,
    choices: @Composable (close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val close = { open = false }
    Box {
        ValueRow(icon, title, value) { open = true }
        if (open) Dropdown(close) { choices(close) }
    }
}

@Composable
private fun ValueRow(
    icon: DrawableResource,
    title: String,
    value: FilterRowValue,
    onClick: () -> Unit,
) = FilterValueRow(icon, title, value.text, onClick, set = value.set, spokenValue = value.spoken)

/** All accounts, then each login with the addresses it receives. */
@Composable
private fun AccountChoices(
    draft: InboxFilters,
    options: FilterOptions,
    onEdit: FilterEdit,
    close: () -> Unit,
) {
    val pick = { account: String? ->
        onEdit { it.copy(account = account) }
        close()
    }
    PickRow(
        "All accounts",
        "${options.accounts.size} accounts · ${options.addresses.size} addresses",
        PickMark(draft.account == null, style = PickStyle.Card),
        { pick(null) },
    ) { Symbol(Res.drawable.symbol_all_inbox) }
    options.accounts.forEach { account ->
        val (title, subtitle) = account.lines(options)
        PickRow(
            title,
            subtitle,
            PickMark(draft.account == account.id, style = PickStyle.Card),
            { pick(account.id) },
        ) {
            PersonAvatar(account.avatar, ComposeTokens.dropdownAvatarSize)
        }
    }
}

/** The login's first address, and its name and other addresses under it when there are any. */
private fun FilterAccount.lines(options: FilterOptions): Pair<String, String?> {
    val addresses = options.addresses.filter { it.address.accountId == id }.map { it.address.address }
    val title = addresses.firstOrNull() ?: name
    val others = addresses.drop(1).takeIf { it.isNotEmpty() }?.let { "also ${it.joinToString()}" }
    return title to listOfNotNull(name.takeIf { it != title }, others).joinToString(" · ").ifEmpty { null }
}

/** The date presets, then Custom…, which [onCustom] opens a range picker for. */
@Composable
private fun DateChoices(
    date: DateFilter,
    onEdit: FilterEdit,
    close: () -> Unit,
    onCustom: () -> Unit,
) {
    DATE_PRESETS.forEach { preset ->
        PickRow(
            preset.label,
            null,
            PickMark(date == preset, style = PickStyle.Card),
            {
                onEdit { it.copy(date = preset) }
                close()
            },
        )
    }
    PickRow(
        "Custom…",
        null,
        PickMark(date is DateFilter.Range, style = PickStyle.Card),
        {
            close()
            onCustom()
        },
    )
}

/** The range picker search uses, applying its range on Done; Clear and Done go back to any time. */
@Composable
private fun DateRangeSheet(
    date: DateFilter,
    dateFormatter: DateFormatter,
    onEdit: FilterEdit,
    onDismiss: () -> Unit,
) {
    val range = date as? DateFilter.Range
    var start by remember { mutableStateOf(range?.from?.toString()) }
    var end by remember { mutableStateOf(range?.to?.toString()) }
    ListSheet(
        "Date",
        onDismiss,
        done =
            UiAction("Done", onClick = {
                val picked = start?.let { DateFilter.Range(LocalDate.parse(it), LocalDate.parse(end ?: it)) }
                onEdit { it.copy(date = picked ?: DateFilter.Any) }
                onDismiss()
            }),
        onClear = {
            start = null
            end = null
        },
        scrolls = true,
    ) {
        DateRangePicker(start, end, dateFormatter) { first, last ->
            start = first
            end = last
        }
    }
}

private val DATE_PRESETS =
    listOf(DateFilter.Any, DateFilter.Today, DateFilter.Last7, DateFilter.Last30, DateFilter.ThisYear)
