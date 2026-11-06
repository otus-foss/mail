package com.otus.mail.feature.folders.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.FolderNode
import com.otus.mail.core.mailbox.folderTree
import com.otus.mail.core.messages.domain.MailMessage

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
internal fun AccountFolders.ruleFolders(): List<FolderNode> =
    folderTree { !it.isCustom }.filter { it.folder.rights.mayAddItems }
