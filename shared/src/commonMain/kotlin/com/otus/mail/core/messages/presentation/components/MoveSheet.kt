package com.otus.mail.core.messages.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import com.otus.designsystem.mail.folders.components.FolderName
import com.otus.designsystem.mail.folders.components.SuggestedFolderRow
import com.otus.designsystem.mail.folders.elements.DestinationPill
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_auto_filter
import com.otus.designsystem.resources.symbol_bookmark
import com.otus.designsystem.shared.controls.elements.IconPill
import com.otus.designsystem.shared.overlays.components.SheetFrame
import com.otus.designsystem.shared.people.components.Recipient
import com.otus.designsystem.shared.rows.elements.Caption
import com.otus.designsystem.shared.selection.components.PickerSearch
import com.otus.designsystem.theme.colors.FolderColor
import com.otus.designsystem.theme.tokens.core.Spacing
import com.otus.designsystem.theme.tokens.layout.PageTokens
import com.otus.designsystem.theme.tokens.overlays.SheetTokens
import com.otus.designsystem.theme.tokens.selection.MoveTokens
import com.otus.mail.core.mail.EmailAddressKey
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.mailbox.presentation.READ_LATER_TITLE
import com.otus.mail.core.mailbox.presentation.symbol
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.shownName
import com.otus.mail.core.messages.presentation.MessageActions
import com.otus.mail.core.messages.presentation.MoveTarget
import com.otus.mail.core.messages.presentation.containsSearch
import com.otus.mail.core.messages.presentation.matchesSearch
import com.otus.mail.core.messages.presentation.ownFolders
import com.otus.mail.core.messages.presentation.shortcuts

/**
 * Moves [messages], one or a selection, to the folder the user taps among [targets], through
 * [onLeave] so a page they leave closes first. Search narrows every list. A suggestion and the
 * sort pill need one sender, so they are left out for a selection from several. With the pill on,
 * the tap also adds a rule that sends future mail like the first message to the same folder.
 */
@Composable
internal fun MoveSheet(
    messages: List<MailMessage>,
    targets: List<MoveTarget>,
    actions: MessageActions,
    onLeave: (run: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = messages.first()
    val oneSender = messages.distinctBy { EmailAddressKey(it.sender.address) }.size == 1
    var query by rememberSaveable { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val sorting = rememberMoveSorting(actions.moves.futureSorting(first).takeIf { oneSender })
    val folder = rememberSuggestedMove(messages, actions)
    val suggested = targets.firstOrNull { it.folderId == folder?.folderId }
    val move: (MoveTarget) -> Unit = { target ->
        onLeave {
            messages.forEach { actions.moves.onMove(it, target) }
            sorting.rule?.addRule(target, messages)
        }
    }
    val saving = messages.mapNotNull(actions.readLater).filter { it.saves }
    // The folder every message is in now shows dimmed in the list, so the user sees where it is.
    val here = first.location.mailboxId.takeIf { id -> id.isNotEmpty() && messages.all { it.location.mailboxId == id } }
    val listed = if (here == null) targets else actions.moves.listed(first)
    SheetFrame("Move to", onDismiss) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SheetTokens.inset),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            MoveSummary(messages, oneSender)
            PickerSearch(query, { query = it }, "Search folders…", Modifier.onFocusChanged { searching = it.hasFocus })
            val readLater = saving.takeIf { it.isNotEmpty() && READ_LATER_TITLE.containsSearch(query) }
            val shortcuts = targets.shortcuts().filter { it.matchesSearch(query) }
            val suggestion = suggested?.takeIf { it.matchesSearch(query) }
            val folders = listed.ownFolders().filter { it.matchesSearch(query) }
            MovePills(
                onReadLater = readLater?.let { changes -> { onLeave { changes.forEach { it.apply() } } } },
                shortcuts,
                sorting,
                onMove = move,
            )
            if (suggestion != null) MoveSuggestion(suggestion, first.sender, move)
            MoveFolderList(folders, here, searching = query.isNotBlank(), onMove = move)
            if (readLater == null && suggestion == null && (shortcuts + folders).none { it.folderId != here }) {
                Caption(
                    if (query.isBlank()) "No folders to move to." else "No folders match.",
                    RowTextInset,
                    heading = false,
                )
            }
        }
        // The keyboard leaves little room, so the folders get it while the user types.
        if (!(searching && keyboardOpen)) MoveSortFooter(sorting)
    }
}

/**
 * Inbox, Read later, then the other system folders, as one wrapping row of pills, with the pill that also sorts
 * future mail at the end; nothing when there are none.
 */
@Composable
private fun MovePills(
    onReadLater: (() -> Unit)?,
    shortcuts: List<MoveTarget>,
    sorting: MoveSorting,
    onMove: (MoveTarget) -> Unit,
) {
    if (onReadLater == null && shortcuts.isEmpty() && sorting.future == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.inlineGap)) {
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.inlineGap)) {
            // Inbox leads whenever the message can go back there, so the pills keep one order.
            val (inbox, others) = shortcuts.partition { it.shortcut == MailboxRole.Inbox }
            inbox.forEach { ShortcutPill(it, onMove) }
            if (onReadLater != null) DestinationPill(READ_LATER_TITLE, Res.drawable.symbol_bookmark, onReadLater)
            others.forEach { ShortcutPill(it, onMove) }
        }
        if (sorting.future != null) {
            IconPill(
                SORT_FUTURE_MAIL,
                Res.drawable.symbol_auto_filter,
                sorting.on,
                { sorting.onToggle(!sorting.on) },
                size = MoveTokens.pillHeight,
            )
        }
    }
}

@Composable
private fun ShortcutPill(
    target: MoveTarget,
    onMove: (MoveTarget) -> Unit,
) {
    val shortcut = target.shortcut ?: return
    DestinationPill(shortcut.title, shortcut.symbol, { onMove(target) })
}

/**
 * The user's own folder [sender]'s mail usually goes to, under its own caption, as the sender's
 * pill and the folder's pill, lined up with the caption.
 */
@Composable
private fun MoveSuggestion(
    target: MoveTarget,
    sender: MailSender,
    onMove: (MoveTarget) -> Unit,
) {
    Caption("Suggested", RowTextInset)
    val name = sender.shownName
    SuggestedFolderRow(
        Recipient(name, sender.chipAvatar, sender.address),
        FolderName(target.path ?: target.folderPath, target.color?.let(FolderColor::named), system = false),
        "Move to ${target.label}, where $name's mail usually goes",
        { onMove(target) },
        RowTextInset,
    )
}

/** Lines captions up with the text inside the settings rows below them. */
internal val RowTextInset = Modifier.padding(horizontal = PageTokens.textInset)

internal const val SORT_FUTURE_MAIL = "Also sort future mail"
