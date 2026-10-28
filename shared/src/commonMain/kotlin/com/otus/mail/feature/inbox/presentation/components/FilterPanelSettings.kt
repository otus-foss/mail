package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.resources.symbol_mark_email_unread
import com.otus.designsystem.resources.symbol_person
import com.otus.designsystem.resources.symbol_swap_vert
import com.otus.designsystem.resources.symbol_tune
import com.otus.designsystem.shared.settings.components.SettingRow
import com.otus.designsystem.shared.settings.elements.ValueButton
import com.otus.mail.feature.inbox.domain.FilterSection
import com.otus.mail.feature.inbox.domain.QuickFacet
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import org.jetbrains.compose.resources.DrawableResource

/**
 * Rows for the filter panel in the Inbox settings: which sections it shows and in what order,
 * and which Quick filters, each chosen in an ordered sheet.
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
        "Quick filters",
        { choosing = FilterSetting.Quick },
        description = "On/off filters and their order",
        icon = Res.drawable.symbol_mark_email_unread,
    ) { ValueButton(shownCount(layout.quick.size)) }
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

        FilterSetting.Quick -> {
            OrderedChoiceSheet(
                "Quick filters",
                layout.quick,
                QuickFacet.entries.map { OrderedChoice(it, it.storageValue, it.label, it.icon, "Quick filters") },
                QuickFacet::storageValue,
                onApply = { quick -> state.update { it.copy(filters = it.filters.copy(quick = quick)) } },
                onDismiss = { choosing = null },
            )
        }

        null -> {}
    }
}

private enum class FilterSetting { Sections, Quick }

private fun shownCount(count: Int): String = if (count == 0) "None" else "$count shown"

private val FilterSection.title: String
    get() =
        when (this) {
            FilterSection.Status -> "Status"
            FilterSection.Quick -> "Quick filters"
            FilterSection.People -> "People"
            FilterSection.Rows -> "Account, folders, date, and metadata"
            FilterSection.Sort -> "Sort"
        }

private val FilterSection.icon: DrawableResource
    get() =
        when (this) {
            FilterSection.Status -> Res.drawable.symbol_mark_email_unread
            FilterSection.Quick -> Res.drawable.symbol_tune
            FilterSection.People -> Res.drawable.symbol_person
            FilterSection.Rows -> Res.drawable.symbol_folder
            FilterSection.Sort -> Res.drawable.symbol_swap_vert
        }
