package com.otus.mail.app

import com.otus.accounts.presentation.AccountsState
import com.otus.mail.feature.folders.domain.ARCHIVE_ROLE
import com.otus.mail.feature.folders.domain.FolderRights
import com.otus.mail.feature.folders.domain.TRASH_ROLE
import com.otus.mail.feature.folders.domain.folderTree
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.folders.presentation.RuleEditorState
import com.otus.mail.feature.folders.presentation.RulesState
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.hasFlag
import com.otus.mail.feature.inbox.domain.withFlag
import com.otus.mail.feature.inbox.presentation.FolderShortcut
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.MoveTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** State holders that outlive a single page. One per composition of [App]. */
internal class MailStates(
    private val services: MailServices,
    private val scope: CoroutineScope,
    val behavior: MailBehaviorState = MailBehaviorState(),
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

    /** Moves, marks, and rules for messages shown by [feed]; [openRule] shows the rule editor for an account. */
    fun messageActions(
        feed: InboxFeedState,
        openRule: (accountId: String) -> Unit,
    ): MessageActions =
        MessageActions(
            moveTargets = ::moveTargets,
            onMove = { message, target -> move(feed, message, target) },
            canMark = ::canMark,
            onMark = { message, flag, on -> mark(feed, message, flag, on) },
            canCreateRule = { rules.account(it.location.accountId)?.support != null },
            onCreateRule = { message ->
                ruleEditor.startForSender(message.location.accountId, message.sender.address)
                openRule(message.location.accountId)
            },
            behavior = { behavior.behavior },
        )

    /**
     * Takes the message out of the list at once and puts it back if the server refuses the move.
     * A successful move does not reload the list: a reload can answer before another move still in
     * flight, which would bring that message back.
     */
    private fun move(
        feed: InboxFeedState,
        message: MailMessage,
        target: MoveTarget,
    ) {
        feed.remove(message.id)
        scope.launch {
            if (services.mover.move(message, target.folderId)) {
                folders.refresh()
            } else {
                feed.restore(message)
                feed.report("Couldn't move the message to ${target.label}. Try again.")
            }
        }
    }

    /** Shows the change at once and puts the message back as it was if the server refuses it. */
    private fun mark(
        feed: InboxFeedState,
        message: MailMessage,
        flag: MessageFlag,
        on: Boolean,
    ) {
        val before = message.hasFlag(flag)
        feed.change(message.id) { it.withFlag(flag, on) }
        scope.launch {
            if (services.marker.mark(message, flag, on)) {
                // Folder unread counts follow the read mark.
                if (flag == MessageFlag.Read) folders.refresh()
            } else {
                feed.change(message.id) { it.withFlag(flag, before) }
                feed.report("Couldn't change the message. Try again.")
            }
        }
    }

    /** Folders of the message's account that accept mail, except the one it is in. */
    private fun moveTargets(message: MailMessage): List<MoveTarget> {
        val account = folders.account(message.location.accountId)
        if (account == null || !rightsOf(message).mayRemoveItems) return emptyList()
        return folderTree(account.folders)
            .filter { it.folder.id != message.location.mailboxId && it.folder.rights.mayAddItems }
            .map { MoveTarget(it.folder.id, it.path, shortcutFor(it.folder.role)) }
    }

    /** Supplied messages have no server id, so only loaded mail can change its marks. */
    private fun canMark(
        message: MailMessage,
        flag: MessageFlag,
    ): Boolean = message.emailId.isNotEmpty() && rightsOf(message).allow(flag)

    /** Rights of the folder the message is shown in. Unknown folders allow everything; the server decides. */
    private fun rightsOf(message: MailMessage): FolderRights =
        folders
            .account(message.location.accountId)
            ?.folder(message.location.mailboxId)
            ?.rights ?: FolderRights()
}

private fun FolderRights.allow(flag: MessageFlag): Boolean =
    when (flag) {
        MessageFlag.Read -> maySetSeen
        MessageFlag.Flagged -> maySetKeywords
    }

private fun shortcutFor(role: String?): FolderShortcut? =
    when (role) {
        ARCHIVE_ROLE -> FolderShortcut.Archive
        TRASH_ROLE -> FolderShortcut.Trash
        else -> null
    }
