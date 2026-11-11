package com.otusfoss.mail.feature.read.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.otusfoss.core.designsystem.mail.attachments.components.AttachmentActions
import com.otusfoss.core.designsystem.mail.attachments.components.AttachmentFile
import com.otusfoss.core.designsystem.mail.attachments.components.AttachmentList
import com.otusfoss.core.designsystem.mail.quickview.components.QuickFiles
import com.otusfoss.core.designsystem.mail.quickview.components.QuickJump
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_code
import com.otusfoss.core.designsystem.shared.controls.elements.Chip
import com.otusfoss.core.designsystem.shared.controls.elements.IconButton
import com.otusfoss.core.designsystem.shared.controls.elements.Symbol
import com.otusfoss.core.designsystem.shared.overlays.elements.sheetInset
import com.otusfoss.core.designsystem.shared.panels.components.GrownPanelLayers
import com.otusfoss.core.designsystem.shared.panels.components.PanelExpansion
import com.otusfoss.core.designsystem.shared.panels.components.grownStopHeight
import com.otusfoss.core.designsystem.shared.panels.components.quickViewHeight
import com.otusfoss.core.designsystem.shared.panels.components.rememberLastStopShows
import com.otusfoss.core.designsystem.shared.panels.components.rememberPanelExpansion
import com.otusfoss.core.designsystem.shared.rows.components.DetailRows
import com.otusfoss.core.designsystem.shared.settings.components.SettingPageRow
import com.otusfoss.core.designsystem.theme.tokens.layout.QuickViewTokens
import com.otusfoss.mail.core.mailbox.presentation.symbol
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.OwnAddresses
import com.otusfoss.mail.core.messages.presentation.MoveTarget
import com.otusfoss.mail.core.messages.presentation.actions.offerFolder
import com.otusfoss.mail.core.messages.presentation.actions.offerMove
import com.otusfoss.mail.core.messages.presentation.actions.symbol
import com.otusfoss.mail.core.messages.presentation.attachmentSummary
import com.otusfoss.mail.core.messages.presentation.components.rememberSuggestedMove
import com.otusfoss.mail.core.messages.presentation.ownFolders
import com.otusfoss.mail.core.messages.presentation.shortcuts
import com.otusfoss.mail.core.settings.domain.ReadQuickModule
import com.otusfoss.mail.feature.read.presentation.ReadSheet
import com.otusfoss.mail.feature.read.presentation.ReadState

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
    // The expanded stop stays a row shorter than the full one, so long details never swap them.
    val expanded = quick?.let(::grownStopHeight)?.coerceAtMost(details - QuickViewTokens.jumpHeight)
    val expansion = rememberPanelExpansion(listOf(expanded ?: quickViewHeight(modules), details))
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
    val strips: ReadStripActions,
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

    /** The page's files as the panel shows them; null until its body loads and when it has none. */
    fun files(): ReadFiles? {
        val files = parts.content(page)?.attachments?.takeIf { it.isNotEmpty() } ?: return null
        return ReadFiles(
            files.map { it.shown(strips.isKept(page, it), state.thumbnails.of(page, it)) },
            attachmentSummary(files),
            strips.files(page, files),
        )
    }
}

/** A page's files as [shown] in the panel, their [summary] line, and what a tap on one does. */
internal class ReadFiles(
    val shown: List<AttachmentFile>,
    val summary: String,
    val actions: AttachmentActions,
)

/**
 * The Read view's grown room: the message's details at both stops, which stay still between them;
 * under them the page's files, or the quick view's [modules] when it has none, and the Move to row
 * around the expanded stop, then every file and the message's source toward the full stop, each
 * fading with how far the panel is open.
 */
@Composable
internal fun ReadExpandedContent(
    room: ReadRoom,
    modules: List<ReadQuickModule>,
    onQuickHeight: (Dp) -> Unit,
) {
    val density = LocalDensity.current
    var detailsHeight by remember { mutableStateOf(0.dp) }
    var restHeight by remember { mutableStateOf(0.dp) }
    LaunchedEffect(detailsHeight, restHeight) {
        if (detailsHeight > 0.dp && restHeight > 0.dp) onQuickHeight(detailsHeight + restHeight)
    }
    GrownPanelLayers(
        room.expansion,
        lastStopOpen = rememberLastStopShows(room.expansion),
        quick = {
            // Measured at its full height, also while the panel is still opening toward it.
            Column(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .onSizeChanged { restHeight = with(density) { it.height.toDp() } }
                    .sheetInset()
                    .padding(top = QuickViewTokens.moduleGap),
                verticalArrangement = Arrangement.spacedBy(QuickViewTokens.moduleGap),
            ) {
                val files = room.files()
                if (files != null) {
                    QuickFiles(files.shown, files.actions)
                } else {
                    modules.forEach { module ->
                        when (module) {
                            ReadQuickModule.Thread -> ReadThreadModule(room)
                        }
                    }
                }
                ReadMoveRow(room)
            }
        },
        lastStop = { ReadDetails(room) },
        shared = {
            DetailRows(
                room.look.detailLines(room.page),
                Modifier
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .onSizeChanged { detailsHeight = with(density) { it.height.toDp() } }
                    .sheetInset()
                    .padding(top = QuickViewTokens.moduleGap),
            )
        },
    )
}

/**
 * What the full stop adds under the details: how the message was delivered, its files as a list,
 * and its original source.
 */
@Composable
private fun ReadDetails(room: ReadRoom) {
    val delivered =
        room.parts
            .content(room.page)
            ?.unsubscribe
            ?.deliveredTo
            .orEmpty()
    Column(Modifier.fillMaxWidth().sheetInset()) {
        DetailRows(deliveryLines(room.page, delivered), divided = true)
        room.files()?.let { AttachmentList(it.shown, it.summary, it.actions) }
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
