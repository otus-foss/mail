package com.otusfoss.mail.feature.mailsettings.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_check
import com.otusfoss.core.designsystem.resources.symbol_close
import com.otusfoss.core.designsystem.shared.controls.elements.Symbol
import com.otusfoss.core.designsystem.shared.overlays.components.SheetFrame
import com.otusfoss.core.designsystem.shared.overlays.elements.sheetInset
import com.otusfoss.core.designsystem.shared.rows.components.RowDensity
import com.otusfoss.core.designsystem.shared.rows.elements.Caption
import com.otusfoss.core.designsystem.shared.selection.components.PickerSearch
import com.otusfoss.core.designsystem.shared.settings.components.SettingRow
import com.otusfoss.core.designsystem.theme.tokens.overlays.SheetTokens
import com.otusfoss.mail.core.messages.domain.ActionBarLayout
import com.otusfoss.mail.core.messages.domain.ActionTarget
import com.otusfoss.mail.core.messages.domain.BarButton
import com.otusfoss.mail.core.messages.domain.BarSlot
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.presentation.actions.label
import com.otusfoss.mail.core.messages.presentation.actions.symbol
import com.otusfoss.mail.core.messages.presentation.components.RowTextInset
import com.otusfoss.mail.core.settings.presentation.icon
import com.otusfoss.mail.core.settings.presentation.label

/**
 * What a slot's picker offers: the plain [actions], the [kinds] of button the user completes with
 * a choice of their own, and what those choose [from].
 */
internal class PickerChoices(
    val actions: List<MailAction>,
    val kinds: List<CustomKind>,
    val from: ButtonChoices,
)

/**
 * Picks the action for [slot] of [layout] from [choices], grouped by what each acts on and narrowed
 * by search. Picking applies at once and moves the check; an action already elsewhere in the bar
 * says it will trade places. Each group ends with its [CustomButtonChoice] rows, such as Move to
 * folder. [onRemove], when given, empties the slot and closes the sheet.
 */
@Composable
internal fun ActionPickerSheet(
    slot: BarSlot,
    layout: ActionBarLayout,
    choices: PickerChoices,
    onPick: (BarButton) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val current = layout.actionAt(slot)
    val matches = { label: String -> label.contains(query.trim(), ignoreCase = true) }
    val actions = choices.actions.filter { matches(it.label) }.groupBy { it.target }
    // A kind that picks a folder has nothing to offer without folders, unless the slot holds one to change.
    val kinds =
        choices.kinds
            .filter { matches(it.prompt) }
            .filter { !it.picksFolder || choices.from.folders.isNotEmpty() || current?.customKind == it }
    val groups = (actions.keys + kinds.map { it.target }).distinct()
    SheetFrame(slot.title, onDismiss) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .sheetInset(),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            PickerSearch(query, { query = it }, "Search actions…")
            groups.forEach { target ->
                Column {
                    if (groups.size > 1) Caption(target.title, RowTextInset)
                    actions[target].orEmpty().forEach { ActionChoice(it, layout, slot, current, onPick) }
                    kinds.filter { it.target == target }.forEach {
                        CustomButtonChoice(it, slot, layout, choices.from, onPick)
                    }
                }
            }
            if (groups.isEmpty()) Caption("No actions match.", RowTextInset, heading = false)
            if (onRemove != null) {
                SettingRow(
                    "Remove from bar",
                    onRemove,
                    icon = Res.drawable.symbol_close,
                    density = RowDensity.Compact,
                )
            }
        }
    }
}

@Composable
private fun ActionChoice(
    action: MailAction,
    layout: ActionBarLayout,
    slot: BarSlot,
    current: BarButton?,
    onPick: (BarButton) -> Unit,
) {
    SettingRow(
        action.label,
        { onPick(action) },
        description = layout.swapNote(slot, action),
        icon = action.symbol,
        trailing = { if (action == current) Symbol(Res.drawable.symbol_check) },
        density = RowDensity.Compact,
    )
}

/** Says that picking [button] for [slot] trades places with the slot that holds it, if another does. */
internal fun ActionBarLayout.swapNote(
    slot: BarSlot,
    button: BarButton?,
): String? = button?.let(::slotOf)?.takeIf { it != slot }?.let { "Swaps with ${it.title}" }

/** The slot's name as its picker and the actions that would swap with it call it. */
internal val BarSlot.title: String
    get() =
        when (this) {
            BarSlot.Primary -> "Main button"
            is BarSlot.Secondary -> "Button ${index + 1}"
        }

private val ActionTarget.title: String
    get() =
        when (this) {
            ActionTarget.Messages -> "Messages"
            ActionTarget.OneMessage -> "One message"
            ActionTarget.List -> "Message list"
            ActionTarget.Draft -> "Writing"
            ActionTarget.Anywhere -> "Anywhere"
            ActionTarget.Scheduled -> "Scheduled mail"
        }
