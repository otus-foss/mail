package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.otus.designsystem.shared.selection.components.ChoiceOptions
import com.otus.designsystem.shared.selection.components.ChoiceRow
import com.otus.designsystem.shared.selection.components.ChoiceSheet
import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.FolderNode
import com.otus.mail.core.mailbox.folderTree
import com.otus.mail.core.mailbox.pathText
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.feature.folders.presentation.FolderRulesState
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.folders.presentation.RuleSheetDraft
import com.otus.mail.feature.folders.presentation.components.AddRuleSheet
import com.otus.mail.feature.folders.presentation.sheetActions

/** "Rule for sender" for [address] in [accountId]; [folderId] is set once the user picks a folder. */
internal data class SenderRule(
    val accountId: String,
    val address: String,
    val name: String,
    val folderId: String? = null,
)

/**
 * "Rule for sender" from an open message: the user picks one of the account's own folders, then
 * the folder's Add rule sheet opens with a From rule for the sender.
 */
internal class SenderRules(
    private val rules: FolderRulesState,
    private val folders: FoldersState,
) {
    var request by mutableStateOf<SenderRule?>(null)
        private set

    /** Starts a rule for [message]'s sender; null where its server runs no rules or it has no folders. */
    fun start(message: MailMessage): (() -> Unit)? {
        val accountId = message.location.accountId
        val account = folders.account(accountId)
        if (rules.account(accountId)?.support == null || account?.ruleFolders().isNullOrEmpty()) return null
        val sender = message.sender
        return { request = SenderRule(accountId, sender.address, sender.name.ifBlank { sender.address }) }
    }

    fun choose(folderId: String) {
        request = request?.copy(folderId = folderId)
    }

    /** Closes the folder list unless a folder was just picked, and closes the sheet. */
    fun dismiss(picking: Boolean) {
        if (!picking || request?.folderId == null) request = null
    }
}

/** The account's own folders that accept mail, which rules can file into. */
private fun AccountFolders.ruleFolders(): List<FolderNode> =
    folderTree { !it.isCustom }.filter { it.folder.rights.mayAddItems }

/** The folder list, then the Add rule sheet, for the open "Rule for sender" request. */
@Composable
internal fun SenderRuleSheets(states: MailStates) {
    val senderRules = states.senderRules
    val request = senderRules.request ?: return
    val account = states.folders.account(request.accountId) ?: return
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
        val known = remember(states.feed.messages, request.accountId) { states.knownAddresses(request.accountId) }
        val actions =
            states.rules.sheetActions(folder, editing = null, onClose = { senderRules.dismiss(picking = false) }) {
                states.folders.refresh()
                states.feed.refresh()
            }
        AddRuleSheet(folder.name, RuleSheetDraft.from(request.address), known, actions)
    }
}
