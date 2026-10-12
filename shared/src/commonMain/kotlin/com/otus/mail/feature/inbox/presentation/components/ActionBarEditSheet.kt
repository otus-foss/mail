package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.PreviewCard
import com.otus.designsystem.components.ReorderableColumn
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.components.SheetFrame
import com.otus.designsystem.elements.IconButton
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_add
import com.otus.designsystem.resources.symbol_close
import com.otus.designsystem.resources.symbol_more_horiz
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.SheetTokens
import com.otus.mail.feature.inbox.domain.ActionBarLayout
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.presentation.MailBehaviorState

/**
 * Edits [place]'s bar, saving each change at once. The bar's buttons drag to reorder, and the one
 * on top is the main button. The actions left out add to the end until the bar is full. The menu
 * opposite Done closes the sheet and runs [onCopy].
 */
@Composable
internal fun ActionBarEditSheet(
    state: MailBehaviorState,
    place: ActionBarPlace,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Set before the menu slides the sheet away, so its Done path opens the copy sheet.
    var copyNext by remember { mutableStateOf(false) }
    val layout = state.behavior.savedLayout(place)
    val save = { changed: ActionBarLayout -> state.update { it.copy(actionBar = it.actionBar + (place to changed)) } }
    SheetFrame(
        place.title,
        onDismiss,
        onDone = { if (copyNext) onCopy() else onDismiss() },
        leadingAction = { done ->
            IconButton("Copy to other screens", {
                copyNext = true
                done()
            }, outlined = false) { Symbol(Res.drawable.symbol_more_horiz) }
        },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = SheetTokens.inset),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            PreviewCard(place.marker) { ActionBarPreview(layout) }
            BarButtons(layout, save)
            LeftOut(state.behavior.barChoices(place) - layout.primary - layout.secondary.toSet(), layout, save)
        }
    }
}

@Composable
private fun BarButtons(
    layout: ActionBarLayout,
    save: (ActionBarLayout) -> Unit,
) {
    SheetCaption("Buttons · ${layout.buttons.size} of ${ActionBarLayout.MAX_SECONDARY + 1}")
    SheetCaption("Drag a button to the top to make it the main one.", heading = false)
    ReorderableColumn(
        layout.buttons,
        key = { it.storageValue },
        onMove = { from, to -> save(layout.moved(from, to)) },
        background = OtusColors.bgDark,
    ) { action ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            DragHandle(action.label)
            // The bar always has a main button, so only the others can be removed.
            val slot = layout.secondary.indexOf(action)
            ActionRow(action, Modifier.weight(1f), description = "Main button".takeIf { slot < 0 }) {
                if (slot >= 0) {
                    IconButton("Remove ${action.label}", { save(layout.withSlot(slot, null)) }) {
                        Symbol(Res.drawable.symbol_close)
                    }
                }
            }
        }
    }
}

/** Actions the bar could hold but does not; a full bar keeps their add buttons disabled. */
@Composable
private fun LeftOut(
    actions: List<MailAction>,
    layout: ActionBarLayout,
    save: (ActionBarLayout) -> Unit,
) {
    if (actions.isEmpty()) return
    val full = layout.secondary.size == ActionBarLayout.MAX_SECONDARY
    SheetCaption("Not in the bar")
    if (full) SheetCaption("Remove a button to add one.", heading = false)
    actions.forEach { action ->
        ActionRow(action) {
            IconButton(
                "Add ${action.label}",
                { save(layout.withSlot(layout.secondary.size, action)) },
                enabled = !full,
            ) {
                Symbol(Res.drawable.symbol_add)
            }
        }
    }
}

@Composable
private fun ActionRow(
    action: MailAction,
    modifier: Modifier = Modifier,
    description: String? = null,
    button: @Composable () -> Unit,
) {
    SettingRow(action.label, onClick = null, modifier, description, action.symbol, button)
}
