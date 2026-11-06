package com.otus.mail.feature.folders.domain

import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.SenderBlocks

/**
 * Blocks a sender with a folder rule that files their mail into the account's Junk folder, so the
 * server does it while the app is closed. It lives in app wiring because it joins the inbox's
 * contract to the folders feature's rules.
 */
internal class RuleSenderBlocks(
    private val rules: FolderRuleRepository,
    private val folders: FolderRepository,
) : SenderBlocks {
    override suspend fun block(message: MailMessage): Boolean {
        val address = message.sender.address.trim()
        if (address.isEmpty()) return false
        return try {
            block(message.location.accountId, address)
        } catch (expected: FolderStorageException) {
            false
        }
    }

    /** False when the account runs no rules or has no Junk folder. */
    private suspend fun block(
        accountId: String,
        address: String,
    ): Boolean {
        val account = rules.accounts().firstOrNull { it.accountId == accountId }?.takeIf { it.support != null }
        val junk = account?.let { junkId(accountId) }
        if (account == null || junk == null) return false
        val rule = FolderRule(RuleCondition.Address(AddressField.From, AddressMatch.Exact(address)))
        return if (rule in account.rulesFor(junk)) {
            true
        } else {
            rules.save(account, account.rules.withAdded(junk, rule)) == RuleOutcome.Saved
        }
    }

    /** The Junk folder, from the folders the device kept, or the server when it kept none. */
    private suspend fun junkId(accountId: String): String? =
        folders.cachedAccounts().junkFolderId(accountId) ?: folders.accounts().junkFolderId(accountId)
}

private fun List<AccountFolders>.junkFolderId(accountId: String): String? =
    firstOrNull { it.accountId == accountId }
        ?.folders
        ?.firstOrNull { it.role == MailboxRole.Junk }
        ?.id
