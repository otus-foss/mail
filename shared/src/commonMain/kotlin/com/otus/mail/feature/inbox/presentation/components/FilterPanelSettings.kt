package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_alternate_email
import com.otus.designsystem.resources.symbol_attach_file
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.resources.symbol_grid_on
import com.otus.designsystem.resources.symbol_group
import com.otus.designsystem.resources.symbol_mark_email_unread
import com.otus.designsystem.resources.symbol_person
import com.otus.designsystem.resources.symbol_swap_vert
import com.otus.designsystem.resources.symbol_tune
import com.otus.designsystem.shared.selection.components.OrderedChoice
import com.otus.designsystem.shared.selection.components.OrderedChoiceSheet
import com.otus.designsystem.shared.settings.components.DependentSettings
import com.otus.designsystem.shared.settings.components.SettingRow
import com.otus.designsystem.shared.settings.components.SettingsSection
import com.otus.designsystem.shared.settings.elements.ValueButton
import com.otus.mail.core.messages.domain.QuickFacet
import com.otus.mail.core.settings.domain.FilterSection
import com.otus.mail.core.settings.domain.QuickViewModule
import com.otus.mail.core.settings.presentation.MailBehaviorState
import org.jetbrains.compose.resources.DrawableResource

/**
 * The filter panel's page: which sections the panel shows and in what order, which Quick filters
 * its Quick filters section offers, and the modules of the quick view it opens to when pulled
 * down, each chosen in an ordered sheet. Quick filters waits until the panel shows its section, so
 * the row stays in place but disabled until then.
 */
@Composable
internal fun FilterPanelSettings(state: MailBehaviorState) {
    var choosing by rememberSaveable { mutableStateOf<FilterSetting?>(null) }
    val layout = state.behavior.filters
    val quickShown = FilterSection.Quick in layout.sections
    SettingsSection("Panel") {
        SettingRow(
            "Sections",
            { choosing = FilterSetting.Sections },
            icon = Res.drawable.symbol_tune,
        ) { ValueButton(shownCount(layout.sections.size)) }
        DependentSettings(enabled = quickShown) {
            SettingRow(
                "Quick filters",
                { choosing = FilterSetting.Quick },
                description = QUICK_FILTERS_UNAVAILABLE.takeUnless { quickShown },
                icon = Res.drawable.symbol_mark_email_unread,
            ) { ValueButton(shownCount(layout.quick.size)) }
        }
    }
    SettingsSection("Pull down") {
        SettingRow(
            "Quick view",
            { choosing = FilterSetting.QuickView },
            icon = Res.drawable.symbol_grid_on,
            description = "Pull the top panel down to see it",
        ) { ValueButton(shownCount(state.behavior.quickView.size)) }
    }
    when (choosing) {
        FilterSetting.Sections -> SectionChoices(state) { choosing = null }
        FilterSetting.Quick -> QuickChoices(state) { choosing = null }
        FilterSetting.QuickView -> QuickViewChoices(state) { choosing = null }
        null -> Unit
    }
}

/** Why Quick filters is disabled while the panel leaves out its section; search shows it too. */
internal const val QUICK_FILTERS_UNAVAILABLE = "Show the Quick filters section to choose them"

@Composable
private fun SectionChoices(
    state: MailBehaviorState,
    onDismiss: () -> Unit,
) {
    OrderedChoiceSheet(
        "Sections",
        state.behavior.filters.sections,
        FilterSection.entries.map { OrderedChoice(it, it.storageValue, it.title, it.icon, "Sections") },
        FilterSection::storageValue,
        onApply = { sections -> state.update { it.copy(filters = it.filters.copy(sections = sections)) } },
        onDismiss = onDismiss,
    )
}

@Composable
private fun QuickChoices(
    state: MailBehaviorState,
    onDismiss: () -> Unit,
) {
    OrderedChoiceSheet(
        "Quick filters",
        state.behavior.filters.quick,
        QuickFacet.entries.map { OrderedChoice(it, it.storageValue, it.label, it.icon, "Quick filters") },
        QuickFacet::storageValue,
        onApply = { quick -> state.update { it.copy(filters = it.filters.copy(quick = quick)) } },
        onDismiss = onDismiss,
    )
}

@Composable
private fun QuickViewChoices(
    state: MailBehaviorState,
    onDismiss: () -> Unit,
) {
    OrderedChoiceSheet(
        "Quick view",
        state.behavior.quickView,
        QuickViewModule.entries.map { OrderedChoice(it, it.storageValue, it.title, it.icon, "Modules") },
        QuickViewModule::storageValue,
        onApply = { modules -> state.update { it.copy(quickView = modules) } },
        onDismiss = onDismiss,
        limit = QuickViewModule.LIMIT,
    )
}

private enum class FilterSetting { Sections, Quick, QuickView }

private val QuickViewModule.title: String
    get() =
        when (this) {
            QuickViewModule.Counts -> "Counts"
            QuickViewModule.People -> "People"
            QuickViewModule.Addresses -> "Addresses"
            QuickViewModule.RecentFiles -> "Recent files"
        }

private val QuickViewModule.icon: DrawableResource
    get() =
        when (this) {
            QuickViewModule.Counts -> Res.drawable.symbol_grid_on
            QuickViewModule.People -> Res.drawable.symbol_group
            QuickViewModule.Addresses -> Res.drawable.symbol_alternate_email
            QuickViewModule.RecentFiles -> Res.drawable.symbol_attach_file
        }

/** How many of a list's choices show, as a settings row's value. */
internal fun shownCount(count: Int): String = if (count == 0) "None" else "$count shown"

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
