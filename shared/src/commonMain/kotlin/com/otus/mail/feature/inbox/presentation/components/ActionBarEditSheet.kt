package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_content_copy
import com.otus.designsystem.shared.controls.elements.IconButton
import com.otus.designsystem.shared.controls.elements.IconButtonLook
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.overlays.components.SheetFrame
import com.otus.designsystem.shared.settings.components.PreviewCard
import com.otus.designsystem.theme.tokens.overlays.SheetTokens
import com.otus.mail.feature.inbox.domain.ActionBarLayout
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.BarButton
import com.otus.mail.feature.inbox.domain.BarSlot
import com.otus.mail.feature.inbox.presentation.MailBehaviorState

/**
 * Edits [place]'s bar, saving each change at once. Tapping a button in the preview, or the Add
 * button after the last one, opens [ActionPickerSheet] for that slot. Its Move to buttons pick among
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
            }, look = IconButtonLook.Plain) { Symbol(Res.drawable.symbol_content_copy) }
        },
    ) {
        Column(
            Modifier.padding(horizontal = SheetTokens.inset),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            PreviewCard(place.marker) { ActionBarPreview(layout, { picking = it }, addSlot = true) }
            SheetCaption("Tap a button to change it.", RowTextInset, heading = false)
        }
    }
    val slot = picking ?: return
    // An empty slot takes only what the bar lacks; a filled one can trade with any button.
    val choices = state.behavior.barChoices(place).filter { layout.actionAt(slot) != null || layout.slotOf(it) == null }
    val moveTo = folders.map(BarButton::MoveTo).filter(place::offers).associateWith { folderColor(it.path) }
    val removable = (slot as? BarSlot.Secondary)?.takeIf { layout.actionAt(it) != null }
    val remove: (() -> Unit)? =
        if (removable == null) {
            null
        } else {
            {
                save(layout.withSlot(removable.index, null))
                picking = null
            }
        }
    ActionPickerSheet(
        slot,
        layout,
        choices,
        moveTo,
        onPick = { save(layout.with(slot, it)) },
        onRemove = remove,
    ) { picking = null }
}
