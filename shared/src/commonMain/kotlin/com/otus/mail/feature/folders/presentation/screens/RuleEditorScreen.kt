package com.otus.mail.feature.folders.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.designsystem.components.ActionDialog
import com.otus.designsystem.components.DialogAction
import com.otus.designsystem.components.PageNote
import com.otus.designsystem.components.Subpage
import com.otus.mail.feature.folders.domain.ruleProblem
import com.otus.mail.feature.folders.domain.without
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.folders.presentation.RuleEditorState
import com.otus.mail.feature.folders.presentation.RulesState
import com.otus.mail.feature.folders.presentation.components.RuleEditorActions
import com.otus.mail.feature.folders.presentation.components.RuleEditorForm
import com.otus.mail.feature.folders.presentation.message

/** Edits one rule of [accountId], or a new one when [ruleId] is null. Saving returns to the rules. */
@Composable
fun RuleEditorScreen(
    editor: RuleEditorState,
    state: RulesState,
    folders: FoldersState,
    accountId: String,
    ruleId: String?,
    onDone: () -> Unit,
) {
    var confirmReplace by rememberSaveable { mutableStateOf(false) }
    val account = state.account(accountId)
    // Opened directly, for example after the app was restored: start from the saved rule.
    LaunchedEffect(accountId, ruleId, account != null) {
        if (account != null && editor.draft?.isFor(accountId, ruleId) != true) editor.edit(accountId, ruleId)
    }
    val leave = {
        editor.close()
        onDone()
    }
    Subpage(if (ruleId == null) "New rule" else "Edit rule", leave) {
        val draft = editor.draft?.takeIf { it.isFor(accountId, ruleId) }
        val support = account?.support
        if (draft == null || support == null) {
            PageNote(if (state.loading) "Loading…" else "This account's rules aren't available.")
            return@Subpage
        }
        PageNote("For ${account.accountName}")
        state.notice?.let { PageNote(it) }
        val accountFolders = folders.account(accountId)
        val folderExists = accountFolders?.folder(draft.folderId.orEmpty()) != null
        val save = { editor.save(onSaved = onDone) }
        RuleEditorForm(
            draft,
            support,
            accountFolders,
            ruleProblem(draft.toRule(draft.ruleId ?: ""), support, folderExists)?.message(),
            state.busy || folders.busy,
            RuleEditorActions(
                onChange = editor::update,
                onCreateFolder = { name ->
                    folders.create(
                        accountId,
                        name,
                        null,
                    ) { id -> editor.draft?.let { editor.update(it.copy(folderId = id)) } }
                },
                onSave = { if (account.changedElsewhere) confirmReplace = true else save() },
                onDelete =
                    draft.ruleId?.let { id ->
                        {
                            state.updateRules(accountId) { it.without(id) }
                            leave()
                        }
                    },
            ),
        )
    }
    if (confirmReplace) {
        ActionDialog(
            "Replace edits made elsewhere?",
            listOf(DialogAction("Replace and save", { editor.save(onSaved = onDone) }, primary = true)),
            onDismiss = { confirmReplace = false },
            message = "These rules were edited in another app. Saving replaces that script with the rules shown here.",
        )
    }
}
