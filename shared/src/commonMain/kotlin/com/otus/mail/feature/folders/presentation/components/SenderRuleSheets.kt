package com.otus.mail.feature.folders.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.otus.designsystem.shared.selection.components.ChoiceOptions
import com.otus.designsystem.shared.selection.components.ChoiceRow
import com.otus.designsystem.shared.selection.components.ChoiceSheet
import com.otus.mail.core.mailbox.FolderNode
import com.otus.mail.core.mailbox.pathText
import com.otus.mail.feature.folders.presentation.FolderRulesState
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.folders.presentation.KnownAddress
import com.otus.mail.feature.folders.presentation.RuleSheetDraft
import com.otus.mail.feature.folders.presentation.SenderRules
import com.otus.mail.feature.folders.presentation.account
import com.otus.mail.feature.folders.presentation.ruleFolders
import com.otus.mail.feature.folders.presentation.sheetActions

/**
 * The folder list, then the Add rule sheet, for the open "Rule for sender" request. [known] are the
 * addresses an account knows, and [onSaved] runs after a rule is saved.
 */
@Composable
internal fun SenderRuleSheets(
    senderRules: SenderRules,
    folders: FoldersState,
    rules: FolderRulesState,
    known: (accountId: String) -> List<KnownAddress>,
    onSaved: () -> Unit,
) {
    val request = senderRules.request ?: return
    val account = folders.account(request.accountId) ?: return
    val folder = request.folderId?.let(account::folder)
    if (folder == null) {
        ChoiceSheet(
            "Sort mail from ${request.name} into",
            ChoiceOptions<FolderNode?>(account.ruleFolders(), selected = null),
            row = { ChoiceRow(it?.path?.pathText().orEmpty()) },
            onSelect = { node -> if (node != null) senderRules.choose(node.folder.id) },
            onDismiss = { senderRules.dismiss(picking = true) },
        )
    } else {
        val actions =
            rules.sheetActions(folder, editing = null, onClose = { senderRules.dismiss(picking = false) }, onSaved)
        AddRuleSheet(folder.name, RuleSheetDraft.from(request.address), known(request.accountId), actions)
    }
}
