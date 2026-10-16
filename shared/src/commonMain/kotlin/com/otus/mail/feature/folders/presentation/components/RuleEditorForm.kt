package com.otus.mail.feature.folders.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.ChoiceDialog
import com.otus.designsystem.components.InputDialog
import com.otus.designsystem.components.PageNote
import com.otus.designsystem.components.PageSection
import com.otus.designsystem.elements.ActionButton
import com.otus.designsystem.elements.ActionRow
import com.otus.designsystem.elements.InputField
import com.otus.designsystem.elements.LocalIconTint
import com.otus.designsystem.elements.SelectionRow
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.resources.symbol_unfold_more
import com.otus.designsystem.theme.FolderColor
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.FolderNode
import com.otus.mail.feature.folders.domain.INBOX_ROLE
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.RuleField
import com.otus.mail.feature.folders.domain.RuleOperator
import com.otus.mail.feature.folders.domain.RuleSupport
import com.otus.mail.feature.folders.domain.canCreateIn
import com.otus.mail.feature.folders.domain.folderNameProblem
import com.otus.mail.feature.folders.domain.folderTree
import com.otus.mail.feature.folders.presentation.RuleDraft
import com.otus.mail.feature.folders.presentation.message

/**
 * Conditions, then the folder and options. Saving stays disabled while [problem] explains why. The
 * chosen folder's icon shows its [colorOf].
 */
@Composable
fun RuleEditorForm(
    draft: RuleDraft,
    support: RuleSupport,
    folders: AccountFolders?,
    colorOf: (MailFolder) -> String?,
    problem: String?,
    busy: Boolean,
    actions: RuleEditorActions,
) {
    val onChange = actions.onChange
    InputField("Name", draft.name, { onChange(draft.copy(name = it)) }, placeholder = "Optional")
    PageSection("When mail arrives") {
        MatchChoice(draft.matchAll) { onChange(draft.copy(matchAll = it)) }
        draft.conditions.forEachIndexed { index, condition ->
            ConditionEditor(
                condition,
                support,
                position = index + 1,
                onChange = { changed -> onChange(draft.copy(conditions = draft.conditions.replaced(index, changed))) },
                onRemove =
                    { onChange(draft.copy(conditions = draft.conditions.filterIndexed { i, _ -> i != index })) }
                        .takeIf { draft.conditions.size > 1 },
            )
        }
        ActionButton(
            "Add condition",
            {
                onChange(
                    draft.copy(
                        conditions =
                            draft.conditions + RuleCondition(RuleField.From, RuleOperator.Contains, ""),
                    ),
                )
            },
            Modifier.fillMaxWidth(),
        )
    }
    PageSection("Then") {
        FolderChoice(draft, folders, colorOf, actions)
        if (support.canMarkRead) {
            SelectionRow("Mark as read", draft.markRead, { onChange(draft.copy(markRead = !draft.markRead)) })
        }
        if (draft.conditions.any { it.field == RuleField.Body }) {
            PageNote("Rules that check the body only apply to mail that arrives from now on.")
        } else {
            SelectionRow(
                "Also move matching mail already in the inbox",
                draft.applyToInbox,
                { onChange(draft.copy(applyToInbox = !draft.applyToInbox)) },
            )
        }
    }
    problem?.let { PageNote(it) }
    ActionButton(
        if (busy) "Saving…" else "Save rule",
        actions.onSave,
        Modifier.fillMaxWidth(),
        primary = true,
        enabled = problem == null && !busy,
    )
    actions.onDelete?.let { ActionButton("Delete rule", it, Modifier.fillMaxWidth(), enabled = !busy) }
}

@Composable
private fun MatchChoice(
    matchAll: Boolean,
    onChange: (Boolean) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    ActionRow(
        "Match",
        { open = true },
        Res.drawable.symbol_unfold_more,
        summary = matchLabel(matchAll),
    )
    if (open) {
        ChoiceDialog("Match", listOf(true, false), matchAll, ::matchLabel, onChange, onDismiss = { open = false })
    }
}

private fun matchLabel(matchAll: Boolean) = if (matchAll) "All conditions" else "Any condition"

/** Rules move mail out of the inbox, so the inbox itself is not offered. */
@Composable
private fun FolderChoice(
    draft: RuleDraft,
    folders: AccountFolders?,
    colorOf: (MailFolder) -> String?,
    actions: RuleEditorActions,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val options =
        folders
            ?.let { account ->
                account.folderTree().filter { it.folder.role != INBOX_ROLE && it.folder.rights.mayAddItems }
            }.orEmpty()
    val chosen = options.firstOrNull { it.folder.id == draft.folderId }
    CompositionLocalProvider(LocalIconTint provides FolderColor.named(chosen?.folder?.let(colorOf)).tint) {
        ActionRow(
            "Move to",
            { picking = true },
            Res.drawable.symbol_unfold_more,
            summary = chosen?.path ?: "Choose a folder",
            icon = Res.drawable.symbol_folder,
        )
    }
    if (folders != null && canCreateIn(folders, null)) {
        ActionButton("New folder", { creating = true }, Modifier.fillMaxWidth())
    }
    if (picking) {
        ChoiceDialog<FolderNode?>(
            "Move to",
            options,
            chosen,
            label = { it?.path.orEmpty() },
            onSelect = { node -> node?.let { actions.onChange(draft.copy(folderId = it.folder.id)) } },
            onDismiss = { picking = false },
        )
    }
    if (creating && folders != null) {
        InputDialog(
            title = "New folder",
            label = "Name",
            confirmLabel = "Create",
            onConfirm = actions.onCreateFolder,
            onDismiss = { creating = false },
            problem = { folderNameProblem(it, null, folders)?.message() },
        )
    }
}

private fun <T> List<T>.replaced(
    index: Int,
    value: T,
): List<T> = mapIndexed { i, item -> if (i == index) value else item }
