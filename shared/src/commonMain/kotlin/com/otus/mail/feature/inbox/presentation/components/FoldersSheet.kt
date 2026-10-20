package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.otus.designsystem.components.FolderPickRow
import com.otus.designsystem.components.PickerSearch
import com.otus.designsystem.components.RowDensity
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.components.SheetFrame
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_create_new_folder
import com.otus.designsystem.theme.PageTokens
import com.otus.designsystem.theme.SheetTokens
import com.otus.mail.feature.inbox.domain.FolderChange
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFolder
import com.otus.mail.feature.inbox.presentation.folderSheetLists
import com.otus.mail.feature.inbox.presentation.matchesSearch

/**
 * Adds every email of [message]'s conversation to folders and takes it out of others, with a
 * checkbox per folder because a JMAP email can be in several. [mailboxes] holds each email's
 * mailboxes by Email id; a folder is ticked when any email is in it. The folders come first, the
 * one the user [cameFrom] marked "you're here", then the rest as a collapsible tree. Ticks stay a
 * draft until Done, which runs [onApply] with the folder ids that changed. Done is off, with the
 * reason, while an email would be left in no folder, which JMAP does not allow. [onNewFolder]
 * adds a New folder row.
 */
@Composable
fun FoldersSheet(
    message: MailMessage,
    mailboxes: Map<String, Set<String>>,
    folders: List<MessageFolder>,
    cameFrom: String?,
    onApply: (added: Set<String>, removed: Set<String>) -> Unit,
    onNewFolder: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val start = mailboxes.values.flatten().toSet()
    var checked by remember(message.id) { mutableStateOf(start) }
    var query by rememberSaveable { mutableStateOf("") }
    val lists = remember(start, folders) { folderSheetLists(start, folders) }
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
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            Column(
                Modifier.padding(horizontal = SheetTokens.inset),
                verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
            ) {
                MoveSummary(listOf(message), oneSender = true)
                PickerSearch(query, { query = it }, "Search folders…")
                if (!change.keepsEveryEmail) SheetCaption("Each mail needs a folder", heading = false)
            }
            val current = lists.current.filter { it.matchesSearch(query) }
            val others = lists.others.filter { it.matchesSearch(query) }
            FolderGroup(currentCaption(lists.current.size), current, checked, toggle) { folder ->
                "you're here".takeIf { folder.id == cameFrom }
            }
            OtherFolderTree(others, query.isNotBlank(), checked, toggle)
            if (current.isEmpty() && others.isEmpty()) {
                SheetCaption(
                    if (query.isBlank()) "No folders yet." else "No folders match.",
                    Modifier.padding(horizontal = SheetTokens.inset),
                    heading = false,
                )
            }
            onNewFolder?.let { NewFolderRow(it) }
        }
    }
}

/** A captioned list of folders with their checkboxes; nothing when [folders] is empty. */
@Composable
private fun FolderGroup(
    caption: String,
    folders: List<MessageFolder>,
    checked: Set<String>,
    onToggle: (String) -> Unit,
    detail: (MessageFolder) -> String?,
) {
    if (folders.isEmpty()) return
    Column {
        SheetCaption(caption, Modifier.padding(horizontal = SheetTokens.inset))
        folders.forEach { folder ->
            FolderPickRow(folderName(folder), folder.id in checked, { onToggle(folder.id) }, detail = detail(folder))
        }
    }
}

/** Opens the New folder flow; its icon lines up with the folder icons above it. */
@Composable
private fun NewFolderRow(onClick: () -> Unit) {
    SettingRow(
        "New folder",
        onClick,
        Modifier.padding(horizontal = SheetTokens.inset - PageTokens.textInset),
        icon = Res.drawable.symbol_create_new_folder,
        density = RowDensity.Compact,
    )
}

private fun currentCaption(count: Int): String = "In $count ${if (count == 1) "folder" else "folders"}"
