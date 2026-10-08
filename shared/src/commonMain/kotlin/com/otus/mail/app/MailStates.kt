package com.otus.mail.app

import com.otus.mail.feature.accounts.presentation.AccountsState
import com.otus.mail.feature.folders.domain.folderTree
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.folders.presentation.RuleEditorState
import com.otus.mail.feature.folders.presentation.RulesState
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.MoveTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** State holders that outlive a single page. One per composition of [App]. */
internal class MailStates(
    private val services: MailServices,
    private val scope: CoroutineScope,
) {
    val feed = InboxFeedState(services.mail, scope, services.avatars)
    val folders = FoldersState(services.folders, scope)
    val rules = RulesState(services.rules, scope)
    val ruleEditor = RuleEditorState(rules)
    val accounts =
        AccountsState(services.accounts, scope) {
            feed.refresh()
            folders.refresh()
            rules.refresh()
        }

    /** A folder page loads only that folder; avatars share the inbox's cache. */
    fun folderFeed(page: MailPage.Folder): InboxFeedState =
        InboxFeedState(
            services.mail,
            scope,
            services.avatars,
            mailScope = MailScope.Folder(page.accountId, page.folderId),
        )

    /** Mail and folder counts change together, so a pull refreshes both. */
    fun refreshInbox() {
        feed.refresh()
        folders.refresh()
    }

    /** Moves and rules for messages shown by [feed]; [openRule] shows the rule editor for an account. */
    fun messageActions(
        feed: InboxFeedState,
        openRule: (accountId: String) -> Unit,
    ): MessageActions =
        MessageActions(
            moveTargets = ::moveTargets,
            onMove = { message, target ->
                scope.launch {
                    if (services.mover.move(message, target.folderId)) {
                        feed.refresh()
                        folders.refresh()
                    } else {
                        feed.report("Couldn't move the message to ${target.label}. Try again.")
                    }
                }
            },
            canCreateRule = { rules.account(it.location.accountId)?.support != null },
            onCreateRule = { message ->
                ruleEditor.startForSender(message.location.accountId, message.sender.address)
                openRule(message.location.accountId)
            },
        )

    /** Folders of the message's account that accept mail, except the one it is in. */
    private fun moveTargets(message: MailMessage): List<MoveTarget> {
        val account = folders.account(message.location.accountId) ?: return emptyList()
        return folderTree(account.folders)
            .filter { it.folder.id != message.location.mailboxId && it.folder.rights.mayAddItems }
            .map { MoveTarget(it.folder.id, it.path) }
    }
}
