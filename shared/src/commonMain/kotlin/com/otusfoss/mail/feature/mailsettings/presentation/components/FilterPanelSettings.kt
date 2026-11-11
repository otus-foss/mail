package com.otusfoss.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_alternate_email
import com.otusfoss.core.designsystem.resources.symbol_attach_file
import com.otusfoss.core.designsystem.resources.symbol_folder
import com.otusfoss.core.designsystem.resources.symbol_grid_on
import com.otusfoss.core.designsystem.resources.symbol_group
import com.otusfoss.core.designsystem.resources.symbol_mark_email_unread
import com.otusfoss.core.designsystem.resources.symbol_person
import com.otusfoss.core.designsystem.resources.symbol_swap_vert
import com.otusfoss.core.designsystem.resources.symbol_tune
import com.otusfoss.core.designsystem.shared.selection.components.OrderedChoice
import com.otusfoss.core.designsystem.shared.selection.components.OrderedChoiceSheet
import com.otusfoss.core.settings.presentation.components.ChoiceRowSpec
import com.otusfoss.core.settings.presentation.components.SettingsSectionSpec
import com.otusfoss.core.settings.presentation.components.waitingOn
import com.otusfoss.mail.core.messages.domain.QuickFacet
import com.otusfoss.mail.core.messages.presentation.icon
import com.otusfoss.mail.core.messages.presentation.label
import com.otusfoss.mail.core.settings.domain.FilterSection
import com.otusfoss.mail.core.settings.domain.QuickViewModule
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import com.otusfoss.mail.core.settings.presentation.icon
import com.otusfoss.mail.core.settings.presentation.label
import org.jetbrains.compose.resources.DrawableResource

/**
 * The filter panel's page: which sections the panel shows and in what order, which Quick filters
 * its Quick filters section offers, and the modules of the quick view it opens to when pulled
 * down, each chosen in an ordered sheet. Quick filters waits until the panel shows its section, so
 * the row stays in place but disabled until then.
 */
internal fun filterPanelSettings(state: MailBehaviorState): List<SettingsSectionSpec> {
    val layout = state.behavior.list.filters
    val panel =
        listOf(
            ChoiceRowSpec(
                "Sections",
                shownCount(layout.sections.size),
                { SectionChoices(state, it) },
                icon = Res.drawable.symbol_tune,
            ),
            ChoiceRowSpec(
                "Quick filters",
                shownCount(layout.quick.size),
                { QuickChoices(state, it) },
                icon = Res.drawable.symbol_mark_email_unread,
            ).waitingOn(QUICK_FILTERS_UNAVAILABLE.takeUnless { FilterSection.Quick in layout.sections }),
        )
    val pullDown =
        ChoiceRowSpec(
            "Quick view",
            shownCount(state.behavior.list.quickView.size),
            { QuickViewChoices(state, it) },
            icon = Res.drawable.symbol_grid_on,
            description = "Pull the top panel down to see it",
        )
    return listOf(SettingsSectionSpec("Panel", panel), SettingsSectionSpec("Pull down", listOf(pullDown)))
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
        state.behavior.list.filters.sections,
        FilterSection.entries.map { OrderedChoice(it, it.storageValue, it.title, it.icon, "Sections") },
        FilterSection::storageValue,
        onApply = { sections -> state.updateList { it.copy(filters = it.filters.copy(sections = sections)) } },
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
        state.behavior.list.filters.quick,
        QuickFacet.entries.map { OrderedChoice(it, it.storageValue, it.label, it.icon, "Quick filters") },
        QuickFacet::storageValue,
        onApply = { quick -> state.updateList { it.copy(filters = it.filters.copy(quick = quick)) } },
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
        state.behavior.list.quickView,
        QuickViewModule.entries.map { OrderedChoice(it, it.storageValue, it.title, it.icon, "Modules") },
        QuickViewModule::storageValue,
        onApply = { modules -> state.updateList { it.copy(quickView = modules) } },
        onDismiss = onDismiss,
        limit = QuickViewModule.LIMIT,
    )
}

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
