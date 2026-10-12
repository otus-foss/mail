package com.otus.mail.feature.inbox.presentation.components

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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import com.otus.designsystem.components.NamedAvatar
import com.otus.designsystem.components.PickerSearch
import com.otus.designsystem.components.RowDensity
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.components.SheetFrame
import com.otus.designsystem.elements.IconPill
import com.otus.designsystem.elements.SidebarPill
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_archive
import com.otus.designsystem.resources.symbol_bookmark
import com.otus.designsystem.resources.symbol_delete
import com.otus.designsystem.resources.symbol_filter_list
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.resources.symbol_inbox
import com.otus.designsystem.resources.symbol_report
import com.otus.designsystem.theme.PageTokens
import com.otus.designsystem.theme.PanelTokens
import com.otus.designsystem.theme.SheetTokens
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.presentation.FolderShortcut
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.MoveTarget
import com.otus.mail.feature.inbox.presentation.containsSearch
import com.otus.mail.feature.inbox.presentation.matchesSearch
import com.otus.mail.feature.inbox.presentation.ownFolders
import com.otus.mail.feature.inbox.presentation.shortcuts
import org.jetbrains.compose.resources.DrawableResource

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
    val oneSender = messages.distinctBy { it.sender.address.lowercase() }.size == 1
    var query by rememberSaveable { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val sorting = rememberMoveSorting(actions.moves.futureSorting(first).takeIf { oneSender })
    val suggested by produceState<MoveTarget?>(null, first.id, oneSender) {
        val folder = if (oneSender) actions.moves.suggested(first) else null
        value = targets.firstOrNull { it.folderId == folder?.folderId }
    }
    val move: (MoveTarget) -> Unit = { target ->
        onLeave {
            messages.forEach { actions.moves.onMove(it, target) }
            sorting.rule?.addRule(target, messages)
        }
    }
    val saving = messages.mapNotNull(actions.readLater).filter { it.saves }
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
            val readLater = saving.takeIf { it.isNotEmpty() && READ_LATER.containsSearch(query) }
            val shortcuts = targets.shortcuts().filter { it.matchesSearch(query) }
            val suggestion = suggested?.takeIf { it.matchesSearch(query) }
            val folders = targets.ownFolders().filter { it.matchesSearch(query) }
            MovePills(
                onReadLater = readLater?.let { changes -> { onLeave { changes.forEach { it.apply() } } } },
                shortcuts,
                sorting,
                onMove = move,
            )
            suggestion?.let { MoveSuggestion(it, first.sender, move) }
            MoveFolderList(folders, searching = query.isNotBlank(), onMove = move)
            if (readLater == null && suggestion == null && (shortcuts + folders).isEmpty()) {
                SheetCaption(
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
 * Read later, then the system folders, as one wrapping row of pills, with the pill that also sorts
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
    Row(horizontalArrangement = Arrangement.spacedBy(PanelTokens.chipGap)) {
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(PanelTokens.chipGap)) {
            onReadLater?.let { SidebarPill(READ_LATER, Res.drawable.symbol_bookmark, selected = false, it) }
            shortcuts.forEach { target ->
                target.shortcut?.let { SidebarPill(it.label, it.icon, selected = false, { onMove(target) }) }
            }
        }
        sorting.future?.let {
            IconPill(SORT_FUTURE_MAIL, Res.drawable.symbol_filter_list, sorting.on, { sorting.onToggle(!sorting.on) })
        }
    }
}

/** The user's own folder [sender]'s mail usually goes to, under its own caption. */
@Composable
private fun MoveSuggestion(
    target: MoveTarget,
    sender: MailSender,
    onMove: (MoveTarget) -> Unit,
) {
    SheetCaption("Suggested", RowTextInset)
    SettingRow(
        target.name,
        { onMove(target) },
        // A nested folder's path tells it apart; a top-level one would only repeat its name.
        description =
            listOfNotNull(target.label.takeIf { it != target.name }, "where ${sender.name} usually goes")
                .joinToString(" · ")
                .replaceFirstChar { it.uppercase() },
        person = NamedAvatar(sender.name, sender.chipAvatar),
        icon = Res.drawable.symbol_folder,
        density = RowDensity.Compact,
    )
}

/** Lines captions up with the text inside the settings rows below them. */
internal val RowTextInset = Modifier.padding(horizontal = PageTokens.textInset)

private const val READ_LATER = "Read later"

internal const val SORT_FUTURE_MAIL = "Also sort future mail"

/** The system folder's symbol, the same one its action bar button and sidebar entry use. */
internal val FolderShortcut.icon: DrawableResource
    get() =
        when (this) {
            FolderShortcut.Inbox -> Res.drawable.symbol_inbox
            FolderShortcut.Archive -> Res.drawable.symbol_archive
            FolderShortcut.Spam -> Res.drawable.symbol_report
            FolderShortcut.Trash -> Res.drawable.symbol_delete
        }
