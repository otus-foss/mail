package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.ActionBar
import com.otus.designsystem.components.BarAction
import com.otus.designsystem.components.ChoiceOptions
import com.otus.designsystem.components.ChoiceRow
import com.otus.designsystem.components.ChoiceSheet
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.components.SettingsSection
import com.otus.designsystem.components.Subpage
import com.otus.designsystem.elements.ValueButton
import com.otus.designsystem.theme.PageTokens
import com.otus.mail.feature.inbox.domain.ActionBarLayout
import com.otus.mail.feature.inbox.domain.ActionBarMode
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.components.buttonLabel
import com.otus.mail.feature.inbox.presentation.components.description
import com.otus.mail.feature.inbox.presentation.components.label
import com.otus.mail.feature.inbox.presentation.components.symbol

/**
 * Chooses whether each screen has its own action bar or one bar is used everywhere, then the
 * buttons of the bars that mode shows. A preview shows each bar as saved; tapping one of its
 * buttons or its row opens a choice for that slot. Choosing an action that another slot holds
 * swaps the two.
 */
@Composable
fun ActionBarSettingsScreen(
    state: MailBehaviorState,
    onBack: () -> Unit,
) {
    var choosing by rememberSaveable { mutableStateOf<Pair<ActionBarPlace, Int>?>(null) }
    var choosingMode by rememberSaveable { mutableStateOf(false) }
    val mode = state.behavior.actionBarMode
    Subpage("Action bar", onBack) {
        SettingRow("Layout", { choosingMode = true }, description = mode.description) { ValueButton(mode.label) }
        ActionBarPlace.entries.filter { it.adaptive == (mode == ActionBarMode.Adaptive) }.forEach { place ->
            PlaceSection(place, state.behavior.savedLayout(place)) { slot -> choosing = place to slot }
        }
    }
    if (choosingMode) {
        ChoiceSheet(
            "Layout",
            ChoiceOptions(ActionBarMode.entries, mode, MailBehavior().actionBarMode),
            row = { ChoiceRow(it.label, it.description) },
            onSelect = { chosen -> state.update { it.copy(actionBarMode = chosen) } },
            onDismiss = { choosingMode = false },
        )
    }
    choosing?.let { (place, slot) -> SlotChoice(state, place, slot) { choosing = null } }
}

/** [place]'s saved bar and a row per slot; a place with few actions has fewer button slots. */
@Composable
private fun PlaceSection(
    place: ActionBarPlace,
    layout: ActionBarLayout,
    onChoose: (slot: Int) -> Unit,
) {
    SettingsSection(place.title) {
        LayoutPreview(layout, onChoose)
        SettingRow("Main button", { onChoose(PRIMARY) }) { ValueButton(layout.primary.label) }
        repeat(minOf(ActionBarLayout.MAX_SECONDARY, place.actions.size - 1)) { slot ->
            SettingRow("Button ${slot + 1}", { onChoose(slot) }) {
                ValueButton(layout.secondary.getOrNull(slot)?.label ?: "None")
            }
        }
    }
}

/** The saved bar; its buttons open their slot's choice instead of acting. */
@Composable
private fun LayoutPreview(
    layout: ActionBarLayout,
    onChoose: (slot: Int) -> Unit,
) {
    fun preview(
        action: MailAction,
        slot: Int,
    ) = BarAction(action.buttonLabel, action.symbol) { onChoose(slot) }
    ActionBar(
        preview(layout.primary, PRIMARY),
        Modifier.padding(vertical = PageTokens.rowGap),
        secondary = layout.secondary.mapIndexed { slot, action -> preview(action, slot) },
    )
}

@Composable
private fun SlotChoice(
    state: MailBehaviorState,
    place: ActionBarPlace,
    slot: Int,
    onDismiss: () -> Unit,
) {
    val behavior = state.behavior
    val layout = behavior.savedLayout(place)
    val choices = behavior.barChoices(place)

    fun save(changed: ActionBarLayout) = state.update { it.copy(actionBar = it.actionBar + (place to changed)) }
    if (slot == PRIMARY) {
        ChoiceSheet(
            "Main button",
            ChoiceOptions(choices, layout.primary, place.defaultLayout.primary),
            row = { ChoiceRow(it.label) },
            onSelect = { save(layout.withPrimary(it)) },
            onDismiss = onDismiss,
        )
    } else {
        // An empty slot cannot trade places with the main button, so it does not offer it.
        val offered = if (slot < layout.secondary.size) choices else choices - layout.primary
        ChoiceSheet(
            "Button ${slot + 1}",
            ChoiceOptions(
                listOf(null) + offered,
                layout.secondary.getOrNull(slot),
                place.defaultLayout.secondary.getOrNull(slot),
            ),
            row = { ChoiceRow(it?.label ?: "None") },
            onSelect = { save(layout.withSlot(slot, it)) },
            onDismiss = onDismiss,
        )
    }
}

private val ActionBarPlace.title: String
    get() =
        when (this) {
            ActionBarPlace.Inbox -> "Message list"
            ActionBarPlace.Message -> "Open message"
            ActionBarPlace.Selection -> "Selected messages"
            ActionBarPlace.Compose -> "Writing"
            ActionBarPlace.Everywhere -> "Every screen"
        }

/** The slot index that stands for the main button. */
private const val PRIMARY = -1
