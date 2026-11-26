package com.otusfoss.mail.feature.mailsettings.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_content_copy
import com.otusfoss.core.designsystem.shared.controls.elements.IconButton
import com.otusfoss.core.designsystem.shared.controls.elements.IconButtonLook
import com.otusfoss.core.designsystem.shared.controls.elements.Symbol
import com.otusfoss.core.designsystem.shared.controls.elements.UiAction
import com.otusfoss.core.designsystem.shared.overlays.components.SheetFrame
import com.otusfoss.core.designsystem.shared.overlays.elements.sheetInset
import com.otusfoss.core.designsystem.shared.rows.elements.Caption
import com.otusfoss.core.designsystem.shared.settings.components.PreviewCard
import com.otusfoss.core.designsystem.theme.tokens.overlays.SheetTokens
import com.otusfoss.mail.core.messages.domain.ActionBarLayout
import com.otusfoss.mail.core.messages.domain.ActionBarPlace
import com.otusfoss.mail.core.messages.domain.BarSlot
import com.otusfoss.mail.core.messages.presentation.components.RowTextInset
import com.otusfoss.mail.core.settings.domain.MailBehavior
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import com.otusfoss.mail.core.settings.presentation.icon

/**
 * Edits [place]'s bar, saving each change at once. Tapping a button in the preview, or the Add
 * button after the last one, opens [ActionPickerSheet] for that slot. Its folder buttons pick among
 * [folders], the paths of every account's own folders, each icon in its [folderColor]. The copy
 * button opposite Done closes the sheet and runs [onCopy].
 */
@Composable
internal fun ActionBarEditSheet(
    state: MailBehaviorState,
    place: ActionBarPlace,
    folders: List<List<String>>,
    folderColor: (path: List<String>) -> String?,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Set before the menu slides the sheet away, so its Done path opens the copy sheet.
    var copyNext by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<BarSlot?>(null) }
    val behavior = state.behavior
    val layout = behavior.actions.savedLayout(place)
    val save = { changed: ActionBarLayout -> state.updateActions { it.copy(bars = it.bars + (place to changed)) } }
    SheetFrame(
        place.title,
        onDismiss,
        done = UiAction("Done", onClick = { if (copyNext) onCopy() else onDismiss() }),
        leadingAction = { done ->
            IconButton("Copy to other screens", {
                copyNext = true
                done()
            }, look = IconButtonLook.Plain) { Symbol(Res.drawable.symbol_content_copy) }
        },
    ) {
        Column(
            Modifier.sheetInset(),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            PreviewCard(place.marker) { ActionBarPreview(layout, { picking = it }, addSlot = true) }
            Caption("Tap a button to change it.", RowTextInset, heading = false)
        }
    }
    val slot = picking ?: return
    ActionPickerSheet(
        slot,
        layout,
        behavior.pickerChoices(place, layout, slot, folders.map { FolderOption(it, folderColor(it)) }),
        onPick = { save(layout.with(slot, it)) },
        onRemove =
            (slot as? BarSlot.Secondary)?.takeIf { layout.actionAt(it) != null }?.let {
                {
                    save(layout.withSlot(it.index, null))
                    picking = null
                }
            },
    ) { picking = null }
}

/**
 * What [slot]'s picker offers at [place]: an empty slot takes only the actions [layout] lacks,
 * while a filled one can trade with any button. The buttons the user completes pick among [folders].
 */
private fun MailBehavior.pickerChoices(
    place: ActionBarPlace,
    layout: ActionBarLayout,
    slot: BarSlot,
    folders: List<FolderOption>,
): PickerChoices =
    PickerChoices(
        actions.barChoices(place, readLater).filter { layout.actionAt(slot) != null || layout.slotOf(it) == null },
        CustomKind.entries.filter { place.offers(it.target) },
        ButtonChoices(folders, actions.filterChoices(readLater)),
    )
