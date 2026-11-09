package com.otus.mail.feature.mailsettings.presentation.components

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
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_check
import com.otus.designsystem.resources.symbol_close
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.overlays.components.SheetFrame
import com.otus.designsystem.shared.overlays.elements.sheetInset
import com.otus.designsystem.shared.rows.components.RowDensity
import com.otus.designsystem.shared.rows.elements.Caption
import com.otus.designsystem.shared.selection.components.PickerSearch
import com.otus.designsystem.shared.settings.components.SettingRow
import com.otus.designsystem.theme.tokens.overlays.SheetTokens
import com.otus.mail.core.messages.domain.ActionBarLayout
import com.otus.mail.core.messages.domain.ActionTarget
import com.otus.mail.core.messages.domain.BarButton
import com.otus.mail.core.messages.domain.BarSlot
import com.otus.mail.core.messages.domain.MailAction
import com.otus.mail.core.messages.presentation.actions.label
import com.otus.mail.core.messages.presentation.actions.symbol
import com.otus.mail.core.messages.presentation.components.RowTextInset
import com.otus.mail.core.settings.presentation.icon
import com.otus.mail.core.settings.presentation.label

/**
 * Picks the action for [slot] of [layout] from [choices], grouped by what each acts on and narrowed
 * by search. Picking applies at once and moves the check; an action already elsewhere in the bar
 * says it will trade places. Where messages can move to [folders], each with its icon color name,
 * the messages group ends in a [MoveToChoice]. [onRemove], when given, empties the slot and closes the sheet.
 */
@Composable
internal fun ActionPickerSheet(
    slot: BarSlot,
    layout: ActionBarLayout,
    choices: List<MailAction>,
    folders: Map<BarButton.MoveTo, String?>,
    onPick: (BarButton) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val current = layout.actionAt(slot)
    val matches = { label: String -> label.contains(query.trim(), ignoreCase = true) }
    val moveTo = (folders.isNotEmpty() || current is BarButton.MoveTo) && matches(MOVE_TO_FOLDER)
    val groups =
        choices.filter { matches(it.label) }.groupBy { it.target }.let { found ->
            if (moveTo) found + (ActionTarget.Messages to found[ActionTarget.Messages].orEmpty()) else found
        }
    SheetFrame(slot.title, onDismiss) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .sheetInset(),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            PickerSearch(query, { query = it }, "Search actions…")
            groups.forEach { (target, actions) ->
                Column {
                    if (groups.size > 1) Caption(target.title, RowTextInset)
                    actions.forEach { action ->
                        ActionChoice(action, layout, slot, current, onPick)
                    }
                    if (moveTo && target == ActionTarget.Messages) MoveToChoice(slot, layout, folders, onPick)
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
