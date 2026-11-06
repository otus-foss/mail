package com.otus.mail.feature.folders.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_filter_alt
import com.otus.designsystem.shared.controls.elements.ActionButton
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.controls.elements.UiAction
import com.otus.designsystem.shared.folders.elements.FolderColorSwatch
import com.otus.designsystem.shared.input.elements.InputField
import com.otus.designsystem.shared.overlays.components.SheetFrame
import com.otus.designsystem.theme.colors.FolderColor
import com.otus.designsystem.theme.tokens.core.Spacing
import com.otus.designsystem.theme.tokens.overlays.SheetTokens
import com.otus.designsystem.theme.typography.panelTextStyle
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.FolderRule
import com.otus.mail.feature.folders.domain.folderNameProblem
import com.otus.mail.feature.folders.presentation.RuleSheetActions
import com.otus.mail.feature.folders.presentation.RuleSheetDraft
import com.otus.mail.feature.folders.presentation.message
import com.otus.mail.feature.folders.presentation.sheetDraft

/**
 * Names a new folder in [account], under [parentId] or at the top level. Add rule and Color sit
 * side by side under the name, and the rules added so far follow as chips that open for editing.
 * Add rule shows only while the account's server keeps rules. The color starts as the next unused
 * one. Create makes the folder, then gives it the color and rules.
 */
@Composable
internal fun NewFolderSheet(
    account: AccountFolders,
    parentId: String?,
    actions: FolderActions,
    onDismiss: () -> Unit,
) {
    val parent = parentId?.let { account.folder(it) }
    var name by rememberSaveable { mutableStateOf("") }
    var setup by remember { mutableStateOf(NewFolderSetup(color = actions.newFolderColor())) }
    var ruleSheet by remember { mutableStateOf<RuleSheetDraft?>(null) }
    var coloring by remember { mutableStateOf(false) }
    val known = actions.ruleAddresses(account.accountId)
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    val problem = folderNameProblem(name, parentId, account)?.message()
    SheetFrame(
        parent?.let { "New folder in ${it.name}" } ?: "New folder",
        onDismiss,
        done =
            UiAction("Create", enabled = problem == null, onClick = {
                actions.onCreate(account.accountId, name, parentId, setup)
                onDismiss()
            }),
    ) {
        Column(
            Modifier.padding(start = SheetTokens.inset, end = SheetTokens.inset, bottom = SheetTokens.inset),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            InputField(
                "Name",
                name,
                { name = it },
                Modifier.focusRequester(focus),
                supportingText = problem.takeIf { name.isNotEmpty() },
            )
            SetupControls(
                setup,
                onAddRule = { ruleSheet = RuleSheetDraft() }.takeIf { known != null },
                onColor = { coloring = true },
                onOpenRule = { index -> ruleSheet = setup.rules[index].sheetDraft(index) },
            )
        }
        // Opened from inside this sheet, they start at least as tall as it.
        ruleSheet?.let { draft ->
            val ruleActions =
                draftRuleActions(draft, setup) {
                    setup = it
                    ruleSheet = null
                }
            AddRuleSheet(name.ifBlank { "the new folder" }, draft, known.orEmpty(), ruleActions)
        }
        if (coloring) {
            FolderColorSheet(setup.color, { setup = setup.copy(color = it) }, onDismiss = { coloring = false })
        }
    }
}

/**
 * The Add rule sheet's actions on the not yet created folder's [setup]: they add the rule, or change
 * or delete the one [draft] is editing, then pass the new setup to [onDone]. Nothing is on the
 * server yet, so no existing mail is counted or moved.
 */
private fun draftRuleActions(
    draft: RuleSheetDraft,
    setup: NewFolderSetup,
    onDone: (NewFolderSetup) -> Unit,
): RuleSheetActions {
    fun done(rules: List<FolderRule>) = onDone(setup.copy(rules = rules))
    val editing = draft.editing
    return RuleSheetActions(
        existingCount = { null },
        onSave = { rule, _ ->
            val replaced = setup.rules.mapIndexed { at, old -> if (at == editing) rule else old }
            done(if (editing == null) setup.rules + rule else replaced)
        },
        onDismiss = { onDone(setup) },
        onDelete = editing?.let { index -> { done(setup.rules.filterIndexed { at, _ -> at != index }) } },
    )
}

/** Add rule, while [onAddRule] is set, beside Color; then the rules added so far, which [onOpenRule] opens. */
@Composable
private fun SetupControls(
    setup: NewFolderSetup,
    onAddRule: (() -> Unit)?,
    onColor: () -> Unit,
    onOpenRule: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(SheetTokens.gap)) {
        onAddRule?.let { SetupButton("Add rule", it, Modifier.weight(1f)) { Symbol(Res.drawable.symbol_filter_alt) } }
        val color = FolderColor.named(setup.color)
        SetupButton(color.label, onColor, Modifier.weight(1f)) { FolderColorSwatch(color) }
    }
    setup.rules.forEachIndexed { index, rule -> FolderRuleChip(rule, Modifier) { onOpenRule(index) } }
}

/** One of the sheet's side-by-side buttons: [leading] then [label]. */
@Composable
private fun SetupButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    leading: @Composable () -> Unit,
) {
    ActionButton(label, onClick, modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.iconGap),
        ) {
            leading()
            Text(label, style = panelTextStyle())
        }
    }
}
