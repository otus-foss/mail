package com.otus.mail.feature.read.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.otus.designsystem.mail.quickview.components.QuickJump
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_code
import com.otus.designsystem.shared.controls.elements.Chip
import com.otus.designsystem.shared.controls.elements.IconButton
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.overlays.elements.sheetInset
import com.otus.designsystem.shared.panels.components.GrownPanelLayers
import com.otus.designsystem.shared.panels.components.PanelExpansion
import com.otus.designsystem.shared.panels.components.grownStopHeight
import com.otus.designsystem.shared.panels.components.quickViewHeight
import com.otus.designsystem.shared.panels.components.rememberLastStopShows
import com.otus.designsystem.shared.panels.components.rememberPanelExpansion
import com.otus.designsystem.shared.rows.components.DetailRows
import com.otus.designsystem.shared.settings.components.SettingPageRow
import com.otus.designsystem.theme.tokens.layout.QuickViewTokens
import com.otus.mail.core.mailbox.presentation.symbol
import com.otus.mail.core.messages.domain.MailAction
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.OwnAddresses
import com.otus.mail.core.messages.presentation.MoveTarget
import com.otus.mail.core.messages.presentation.actions.offerFolder
import com.otus.mail.core.messages.presentation.actions.offerMove
import com.otus.mail.core.messages.presentation.actions.symbol
import com.otus.mail.core.messages.presentation.components.rememberSuggestedMove
import com.otus.mail.core.messages.presentation.ownFolders
import com.otus.mail.core.messages.presentation.shortcuts
import com.otus.mail.core.settings.domain.ReadQuickModule
import com.otus.mail.feature.read.presentation.ReadSheet
import com.otus.mail.feature.read.presentation.ReadState

/**
 * How far the Read view's control panel is dragged open: regular, its usual height; expanded, the
 * quick view, [quick] tall once measured and else as tall as [modules] modules; and full, every
 * detail of the message, [details] tall. Back steps down to regular.
 */
@Composable
internal fun rememberReadExpansion(
    modules: Int,
    quick: Dp?,
    details: Dp,
): PanelExpansion {
    val expansion = rememberPanelExpansion(listOf(quick?.let(::grownStopHeight) ?: quickViewHeight(modules), details))
    NavigationBackHandler(
        rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = expansion.expanded,
        onBackCompleted = expansion::collapse,
    )
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
    onQuickHeight: (Dp) -> Unit,
) {
    val density = LocalDensity.current
    GrownPanelLayers(
        room.expansion,
        lastStopOpen = rememberLastStopShows(room.expansion),
        quick = {
            // Measured at its full height, also while the panel is still opening toward it.
            Column(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .onSizeChanged { onQuickHeight(with(density) { it.height.toDp() }) }
                    .sheetInset()
                    .padding(top = QuickViewTokens.moduleGap),
                verticalArrangement = Arrangement.spacedBy(QuickViewTokens.moduleGap),
            ) {
                DetailRows(room.look.detailLines(room.page))
                modules.forEach { module ->
                    when (module) {
                        ReadQuickModule.Thread -> ReadThreadModule(room)
                        ReadQuickModule.Counts -> ReadCountsModule(room)
                    }
                }
                ReadMoveRow(room)
            }
        },
        lastStop = { ReadDetails(room) },
    )
}

@Composable
private fun ReadDetails(room: ReadRoom) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .sheetInset(),
    ) {
        room.look.Details(room.page)
        SettingPageRow(
            "Show original",
            { room.open(ReadSheet.Details(room.page)) },
            icon = Res.drawable.symbol_code,
        )
    }
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
                actions.offerMove(listOf(row), parts.moveChoice::choose)?.run?.invoke()
            }) { Symbol(MailAction.Move.symbol) }
        }
    QuickJump(move) {
        chips.forEach { target ->
            Chip(target.chipLabel, {
                actions.offerFolder(listOf(row), target)?.let(parts::perform)
            }) { Symbol(target.chipIcon) }
        }
    }
}

private val MoveTarget.chipLabel: String
    get() = shortcut?.title ?: name

private val MoveTarget.chipIcon
    get() = shortcut.symbol

/** Folder chips the Move to row offers before its Move button. */
private const val MOVE_CHIPS = 6
