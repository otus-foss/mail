package com.otus.mail.feature.folders.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.designsystem.components.ActionDialog
import com.otus.designsystem.components.ConfirmDialog
import com.otus.designsystem.components.DialogAction
import com.otus.designsystem.components.PageNote
import com.otus.designsystem.components.Subpage
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.RuleAvailability
import com.otus.mail.feature.folders.domain.scopeOf
import com.otus.mail.feature.folders.presentation.FolderColorsState
import com.otus.mail.feature.folders.presentation.FolderDialog
import com.otus.mail.feature.folders.presentation.FolderRulesState
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.folders.presentation.KnownAddress
import com.otus.mail.feature.folders.presentation.RuleSheetDraft
import com.otus.mail.feature.folders.presentation.canMove
import com.otus.mail.feature.folders.presentation.components.AddRuleSheet
import com.otus.mail.feature.folders.presentation.components.DeleteFolderRow
import com.otus.mail.feature.folders.presentation.components.FolderActions
import com.otus.mail.feature.folders.presentation.components.FolderColorChoice
import com.otus.mail.feature.folders.presentation.components.FolderColorRow
import com.otus.mail.feature.folders.presentation.components.FolderDialogs
import com.otus.mail.feature.folders.presentation.components.FolderNameRow
import com.otus.mail.feature.folders.presentation.components.FolderParentRow
import com.otus.mail.feature.folders.presentation.components.FolderRulesSection
import com.otus.mail.feature.folders.presentation.components.folderActions
import com.otus.mail.feature.folders.presentation.deletable
import com.otus.mail.feature.folders.presentation.message
import com.otus.mail.feature.folders.presentation.parentRules
import com.otus.mail.feature.folders.presentation.remove
import com.otus.mail.feature.folders.presentation.sheetActions
import com.otus.mail.feature.folders.presentation.sheetDraft
import com.otus.mail.feature.folders.presentation.subfolderCount
import com.otus.mail.feature.folders.presentation.summary

/** The state holders a folder page reads and changes. */
class FolderDetailStates(
    val folders: FoldersState,
    val colors: FolderColorsState,
    val rules: FolderRulesState,
)

/** The rule the page is showing a popup for: the Add rule sheet, or the delete-only menu of [locked]. */
internal class RulePopup(
    val sheet: RuleSheetDraft? = null,
    val locked: Int? = null,
)

/**
 * One folder's settings from Settings › Folders & rules: its counts, name, icon color, the rules that sort
 * new mail into it, and Delete once it is empty. A mailbox shared with the login has no color or
 * rules here: colors are for the login's own folders, and Sieve files only into those. [known]
 * holds the addresses of loaded mail for the Add rule sheet's suggestions.
 */
@Composable
fun FolderDetailScreen(
    folder: MailFolder,
    states: FolderDetailStates,
    known: List<KnownAddress>,
    onOpenFolder: (MailFolder) -> Unit,
    onBack: () -> Unit,
) {
    val folders = states.folders
    val colors = states.colors
    val rules = states.rules
    var dialog by rememberSaveable(stateSaver = FolderDialog.Saver) { mutableStateOf<FolderDialog?>(null) }
    var popup by remember { mutableStateOf(RulePopup()) }
    val scope = folders.account(folder.accountId)?.scopeOf(folder)
    val subfolders = scope?.subfolderCount(folder) ?: 0
    val own = folder.sharedIn == null && folder.isCustom
    val id = folder.accountId
    Subpage(folder.name, onBack) {
        PageNote(folder.summary(subfolders))
        listOfNotNull(folders.notice, rules.notice).forEach { PageNote(it) }
        FolderNameRow(
            folder.name,
            { dialog = FolderDialog.Rename(id, folder.id) }.takeIf { folder.isCustom && folder.rights.mayRename },
        )
        if (own) {
            val account = folders.account(id)
            val parent = account?.folder(folder.parentId.orEmpty())
            val move = { dialog = FolderDialog.Move(id, folder.id) }
            FolderParentRow(parent?.name, move.takeIf { account?.canMove(folder) == true })
        }
        if (own && colors.colorable(folder)) {
            FolderColorRow(colors.colors.of(folder)) { dialog = FolderDialog.Color(id, folder.id) }
        }
        if (own) {
            when (val availability = rules.account(id)?.availability) {
                is RuleAvailability.Available -> {
                    val folderRules = rules.rules(folder)
                    FolderRulesSection(
                        folderRules,
                        parentRules(folders.account(id)?.folders.orEmpty(), folder, rules::rules),
                        onOpenParent = onOpenFolder,
                        onOpen = { index ->
                            val draft = folderRules[index].sheetDraft(index)
                            popup = if (draft != null) RulePopup(sheet = draft) else RulePopup(locked = index)
                        },
                        onAdd = { popup = RulePopup(sheet = RuleSheetDraft()) },
                    )
                }

                is RuleAvailability.Failed -> {
                    PageNote(availability.reason.message())
                }

                RuleAvailability.RulesUnavailable, null -> {}
            }
        }
        DeleteFolderRow({ dialog = FolderDialog.Delete(id, folder.id) }.takeIf { folder.deletable(subfolders) })
    }
    FolderDetailDialogs(folder, states, known, dialog, popup, onDialog = { dialog = it }, onPopup = { popup = it })
}

/** The folder popups, the Add rule sheet, the delete-only menu, and the replace confirmation. */
@Composable
private fun FolderDetailDialogs(
    folder: MailFolder,
    states: FolderDetailStates,
    known: List<KnownAddress>,
    dialog: FolderDialog?,
    popup: RulePopup,
    onDialog: (FolderDialog?) -> Unit,
    onPopup: (RulePopup) -> Unit,
) {
    val rules = states.rules
    val close = { onPopup(RulePopup()) }
    dialog?.let { open ->
        val scope = states.folders.account(open.accountId)?.scopeOf(folder)
        FolderDialogs(open, scope, folderActions(states.folders, states.colors), onDialog)
    }
    popup.sheet?.let { draft ->
        val actions = rules.sheetActions(folder, draft.editing, onClose = close, onMoved = states.folders::refresh)
        AddRuleSheet(folder.name, draft, known, actions)
    }
    popup.locked?.let { index ->
        ActionDialog(
            "Can't edit this rule here",
            listOf(DialogAction("Delete rule", { rules.remove(folder, index) })),
            onDismiss = close,
            message = "Only rules on one sender, recipient, subject, or mailing list can be edited here.",
        )
    }
    rules.replaceRequest?.let { request ->
        ConfirmDialog(
            "Replace edits made elsewhere?",
            "These rules were changed in another app. Saving replaces those changes.",
            confirmLabel = "Replace",
            onConfirm = request.replace,
            onDismiss = rules::dismissReplace,
        )
    }
}
