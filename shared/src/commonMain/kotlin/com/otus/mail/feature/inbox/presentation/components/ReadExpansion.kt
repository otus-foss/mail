package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.otus.designsystem.mail.quickview.components.QuickJump
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_code
import com.otus.designsystem.resources.symbol_drive_file_move
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.shared.controls.elements.Chip
import com.otus.designsystem.shared.controls.elements.IconButton
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.panels.components.PanelExpansion
import com.otus.designsystem.shared.panels.components.rememberPanelExpansion
import com.otus.designsystem.shared.settings.components.SettingPageRow
import com.otus.designsystem.theme.tokens.layout.QuickViewTokens
import com.otus.designsystem.theme.tokens.overlays.SheetTokens
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.OwnAddresses
import com.otus.mail.feature.inbox.domain.ReadQuickModule
import com.otus.mail.feature.inbox.presentation.MoveTarget
import com.otus.mail.feature.inbox.presentation.ReadSheet
import com.otus.mail.feature.inbox.presentation.ReadState
import com.otus.mail.feature.inbox.presentation.ownFolders
import com.otus.mail.feature.inbox.presentation.shortcuts

/**
 * How far the Read view's control panel is dragged open: its usual height, the quick view with
 * [modules] modules, and every detail of the message, [details] tall. Back steps down to the usual
 * height.
 */
@Composable
internal fun rememberReadExpansion(
    modules: Int,
    details: Dp,
): PanelExpansion {
    val expansion = rememberPanelExpansion(listOf(quickHeight(modules), details))
    BackHandler(expansion.expanded) { expansion.collapse() }
    return expansion
}

/**
 * What the Read view's grown panel reads and does: the conversation in [state], the [page] on
 * screen, and the user's [own] addresses. [turnTo] shows a page of the conversation and [open] one
 * of its sheets, each bringing the panel back first.
 */
internal class ReadRoom(
    val state: ReadState,
    val page: MailMessage,
    val own: OwnAddresses,
    val expansion: PanelExpansion,
    val parts: ReadParts,
    val look: ReadPageLook,
    private val onPage: (Int) -> Unit,
) {
    fun turnTo(index: Int) {
        expansion.collapse()
        onPage(index)
    }

    fun open(sheet: ReadSheet) {
        expansion.collapse()
        state.sheet = sheet
    }
}

/**
 * The Read view's grown room: the quick view's [modules] and the Move to row around the first stop,
 * then every detail of the message toward the last, each fading with how far the panel is open.
 */
@Composable
internal fun ReadExpandedContent(
    room: ReadRoom,
    modules: List<ReadQuickModule>,
) {
    val expansion = room.expansion
    val position = { expansion.position }
    // A faded layer leaves the accessibility tree too, so a screen reader finds only what shows.
    val quickHidden by remember(expansion) { derivedStateOf { quickAlpha(position()) == 0f } }
    val detailsHidden by remember(expansion) { derivedStateOf { filtersAlpha(position()) == 0f } }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = quickAlpha(position()) }
                .hiddenWhen(quickHidden)
                .padding(horizontal = SheetTokens.inset)
                .padding(top = QuickViewTokens.moduleGap),
            verticalArrangement = Arrangement.spacedBy(QuickViewTokens.moduleGap),
        ) {
            modules.forEach { module ->
                when (module) {
                    ReadQuickModule.Thread -> ReadThreadModule(room)
                    ReadQuickModule.Sender -> ReadSenderModule(room)
                    ReadQuickModule.Counts -> ReadCountsModule(room)
                }
            }
            ReadMoveRow(room)
        }
        // Composed only while it shows, so the faded details over the quick view take no taps.
        if (rememberDetailsShown(expansion, !detailsHidden)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = filtersAlpha(position()) }
                    .hiddenWhen(detailsHidden)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SheetTokens.inset),
            ) {
                room.look.Details(room.page)
                SettingPageRow(
                    "Show original",
                    { room.open(ReadSheet.Details(room.page)) },
                    icon = Res.drawable.symbol_code,
                )
            }
        }
    }
}

/**
 * Whether the details show: while [inSight], and on until the panel stops moving after, so a drag
 * that started in them keeps them under the finger and ends where the panel settles.
 */
@Composable
private fun rememberDetailsShown(
    expansion: PanelExpansion,
    inSight: Boolean,
): Boolean {
    var shown by remember { mutableStateOf(inSight) }
    if (inSight) {
        shown = true
    } else if (!expansion.moving) {
        shown = false
    }
    return shown
}

/**
 * The folders this mail likely goes to, the usual one first, then the system folders and the
 * user's own; a tap moves the conversation and goes back to the list. All folders opens the Move to
 * sheet.
 */
@Composable
private fun ReadMoveRow(room: ReadRoom) {
    val parts = room.parts
    val actions = parts.actions ?: return
    val row = parts.row
    val targets = remember(row) { actions.moves.targets(row) }
    val usual = rememberSuggestedMove(listOf(row), actions)
    val chips =
        (
            listOfNotNull(usual?.let { suggested -> targets.firstOrNull { it.folderId == suggested.folderId } }) +
                targets.shortcuts() + targets.ownFolders()
        ).distinctBy { it.folderId }
            .filter { it.folderId != row.location.mailboxId }
            .take(MOVE_CHIPS)
    val move =
        @Composable {
            IconButton("All folders", {
                room.expansion.collapse()
                parts.moveChoice.choose(listOf(row), targets)
            }) { Symbol(Res.drawable.symbol_drive_file_move) }
        }
    QuickJump(move) {
        chips.forEach { target ->
            Chip(target.chipLabel, {
                parts.close()
                actions.moves.onMove(row, target)
            }) { Symbol(target.chipIcon) }
        }
    }
}

private val MoveTarget.chipLabel: String
    get() = shortcut?.label ?: name

private val MoveTarget.chipIcon
    get() = shortcut?.icon ?: Res.drawable.symbol_folder

/** Folder chips the Move to row offers before its Move button. */
private const val MOVE_CHIPS = 6
