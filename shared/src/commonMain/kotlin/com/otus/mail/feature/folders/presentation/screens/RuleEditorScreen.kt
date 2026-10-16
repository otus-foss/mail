package com.otus.mail.feature.folders.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.otus.designsystem.components.PageNote
import com.otus.designsystem.components.Subpage
import com.otus.mail.feature.folders.domain.ruleProblem
import com.otus.mail.feature.folders.domain.without
import com.otus.mail.feature.folders.presentation.FolderColorsState
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.folders.presentation.RuleEditorState
import com.otus.mail.feature.folders.presentation.RulesState
import com.otus.mail.feature.folders.presentation.components.ReplaceRulesDialog
import com.otus.mail.feature.folders.presentation.components.RuleEditorActions
import com.otus.mail.feature.folders.presentation.components.RuleEditorForm
import com.otus.mail.feature.folders.presentation.message

/** Edits one rule of [accountId], or a new one when [ruleId] is null. Saving returns to the rules. */
@Composable
fun RuleEditorScreen(
    editor: RuleEditorState,
    state: RulesState,
    folders: FoldersState,
    colors: FolderColorsState,
    accountId: String,
    ruleId: String?,
    onDone: () -> Unit,
) {
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
        RuleEditorForm(
            draft,
            support,
            accountFolders,
            colors::colorOf,
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
                onSave = { editor.save(onSaved = onDone) },
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
    state.replaceRequest?.let { ReplaceRulesDialog(it.replace, it.dismiss) }
}
