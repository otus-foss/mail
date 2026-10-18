package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.elements.ValueButton
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_alternate_email
import com.otus.designsystem.resources.symbol_calendar_today
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.resources.symbol_folder_off
import com.otus.designsystem.resources.symbol_mark_email_unread
import com.otus.designsystem.resources.symbol_person
import com.otus.designsystem.resources.symbol_straighten
import com.otus.designsystem.resources.symbol_swap_vert
import com.otus.designsystem.resources.symbol_tune
import com.otus.mail.feature.inbox.domain.FilterSection
import com.otus.mail.feature.inbox.domain.ShowFacet
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import org.jetbrains.compose.resources.DrawableResource

/**
 * Rows for the filter panel in the Inbox settings: which sections it shows and in what order,
 * and which Show choices, each chosen in an ordered sheet.
 */
@Composable
internal fun FilterPanelSettings(state: MailBehaviorState) {
    var choosing by rememberSaveable { mutableStateOf<FilterSetting?>(null) }
    val layout = state.behavior.filters
    SettingRow(
        "Filter panel",
        { choosing = FilterSetting.Sections },
        description = "Sections and their order",
        icon = Res.drawable.symbol_tune,
    ) { ValueButton(shownCount(layout.sections.size)) }
    SettingRow(
        "Show filters",
        { choosing = FilterSetting.Show },
        description = "Choices in the Show section",
        icon = Res.drawable.symbol_mark_email_unread,
    ) { ValueButton(shownCount(layout.show.size)) }
    when (choosing) {
        FilterSetting.Sections -> {
            OrderedChoiceSheet(
                "Filter panel",
                layout.sections,
                FilterSection.entries.map { OrderedChoice(it, it.storageValue, it.title, it.icon, "Sections") },
                FilterSection::storageValue,
                onApply = { sections -> state.update { it.copy(filters = it.filters.copy(sections = sections)) } },
                onDismiss = { choosing = null },
            )
        }

        FilterSetting.Show -> {
            OrderedChoiceSheet(
                "Show filters",
                layout.show,
                ShowFacet.entries.map { OrderedChoice(it, it.storageValue, it.label, it.icon, "Show") },
                ShowFacet::storageValue,
                onApply = { show -> state.update { it.copy(filters = it.filters.copy(show = show)) } },
                onDismiss = { choosing = null },
            )
        }

        null -> {}
    }
}

private enum class FilterSetting { Sections, Show }

private fun shownCount(count: Int): String = if (count == 0) "None" else "$count shown"

private val FilterSection.title: String
    get() =
        when (this) {
            FilterSection.Show -> "Show"
            FilterSection.From -> "From"
            FilterSection.Accounts -> "Account"
            FilterSection.SentTo -> "Sent to"
            FilterSection.ReceivedAs -> "Received as"
            FilterSection.InFolder -> "In folder"
            FilterSection.SkipFolders -> "Skip folders"
            FilterSection.Date -> "Date"
            FilterSection.Size -> "Size"
            FilterSection.Sort -> "Sort"
        }

private val FilterSection.icon: DrawableResource
    get() =
        when (this) {
            FilterSection.Show -> Res.drawable.symbol_mark_email_unread
            FilterSection.From, FilterSection.Accounts -> Res.drawable.symbol_person
            FilterSection.SentTo, FilterSection.ReceivedAs -> Res.drawable.symbol_alternate_email
            FilterSection.InFolder -> Res.drawable.symbol_folder
            FilterSection.SkipFolders -> Res.drawable.symbol_folder_off
            FilterSection.Date -> Res.drawable.symbol_calendar_today
            FilterSection.Size -> Res.drawable.symbol_straighten
            FilterSection.Sort -> Res.drawable.symbol_swap_vert
        }
