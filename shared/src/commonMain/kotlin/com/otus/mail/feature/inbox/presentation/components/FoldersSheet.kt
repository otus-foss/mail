package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import com.otus.designsystem.components.PickerSearch
import com.otus.designsystem.components.SheetFrame
import com.otus.designsystem.elements.DestinationPill
import com.otus.designsystem.elements.IconPill
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_add
import com.otus.designsystem.resources.symbol_bookmark
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.theme.MoveTokens
import com.otus.designsystem.theme.PanelTokens
import com.otus.designsystem.theme.SheetTokens
import com.otus.mail.feature.inbox.domain.FolderChange
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFolder
import com.otus.mail.feature.inbox.presentation.folderSheetChoices
import com.otus.mail.feature.inbox.presentation.matchesSearch

/**
 * Adds every email of [message]'s conversation to folders and takes it out of others, laid out
 * as the Move to sheet is: the system folders and Read later as pills, then the user's own folders as a tree,
 * each ticked when any email is in it, since a JMAP email can be in several. [mailboxes] holds
 * each email's mailboxes by Email id. Ticks stay a draft until Done, which runs [onApply] with the
 * folder ids that changed. Done is off, with the reason, while an email would be left in no
 * folder, which JMAP does not allow. [onNewFolder] adds a + pill after the system folders.
 */
@Composable
fun FoldersSheet(
    message: MailMessage,
    mailboxes: Map<String, Set<String>>,
    folders: List<MessageFolder>,
    onApply: (added: Set<String>, removed: Set<String>) -> Unit,
    onNewFolder: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val start = mailboxes.values.flatten().toSet()
    var checked by remember(message.id) { mutableStateOf(start) }
    var query by rememberSaveable { mutableStateOf("") }
    val choices = remember(folders) { folderSheetChoices(folders) }
    val toggle = { id: String -> checked = if (id in checked) checked - id else checked + id }
    val change = FolderChange(message, mailboxes, added = checked - start, removed = start - checked)
    SheetFrame(
        "Folders",
        onDismiss,
        onDone = {
            if (change.changes) onApply(change.added, change.removed)
            onDismiss()
        },
        doneEnabled = change.keepsEveryEmail,
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SheetTokens.inset),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            MoveSummary(listOf(message), oneSender = true)
            PickerSearch(query, { query = it }, "Search folders…")
            if (!change.keepsEveryEmail) SheetCaption("Each mail needs a folder", heading = false)
            val system = choices.system.filter { it.matchesSearch(query) }
            val own = choices.own.filter { it.matchesSearch(query) }
            SystemFolderPills(system, checked, toggle, onNewFolder)
            FolderCheckTree(own, query.isNotBlank(), checked, toggle)
            if (system.isEmpty() && own.isEmpty()) {
                SheetCaption(
                    if (query.isBlank()) "No folders yet." else "No folders match.",
                    RowTextInset,
                    heading = false,
                )
            }
        }
    }
}

/**
 * The system folders as pills that add the mail to them or take it out, filled while it is in
 * them, then a + pill that runs [onNewFolder]; nothing when there is neither.
 */
@Composable
private fun SystemFolderPills(
    folders: List<MessageFolder>,
    checked: Set<String>,
    onToggle: (String) -> Unit,
    onNewFolder: (() -> Unit)?,
) {
    if (folders.isEmpty() && onNewFolder == null) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(PanelTokens.chipGap),
        verticalArrangement = Arrangement.spacedBy(PanelTokens.chipGap),
    ) {
        folders.forEach { folder ->
            DestinationPill(
                folder.pillLabel,
                if (folder.readLater) {
                    Res.drawable.symbol_bookmark
                } else {
                    folderName(folder).icon
                        ?: Res.drawable.symbol_folder
                },
                { onToggle(folder.id) },
                selected = folder.id in checked,
            )
        }
        onNewFolder?.let { IconPill(NEW_FOLDER, Res.drawable.symbol_add, false, it, size = MoveTokens.pillHeight) }
    }
}

/** The name the app gives a system folder, as the Move to sheet's pills do, else the server's. */
private val MessageFolder.pillLabel: String
    get() = if (readLater) "Read later" else ROLE_LABELS[role] ?: path.last()

private val ROLE_LABELS =
    mapOf("inbox" to "Inbox", "archive" to "Archive", "sent" to "Sent", "junk" to "Spam", "trash" to "Trash")

private const val NEW_FOLDER = "New folder"
