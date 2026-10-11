package com.otus.mail.app

import com.otus.accounts.presentation.AccountsState
import com.otus.mail.feature.folders.domain.FolderRights
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.folders.presentation.RuleEditorState
import com.otus.mail.feature.folders.presentation.RulesState
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.hasFlag
import com.otus.mail.feature.inbox.domain.withFlag
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.MessageActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** State holders that outlive a single page. One per composition of [App]. */
internal class MailStates(
    private val services: MailServices,
    private val scope: CoroutineScope,
    val behavior: MailBehaviorState = MailBehaviorState(),
) {
    val feed = InboxFeedState(services.mail, scope, services.avatars)
    val folders =
        FoldersState(
            services.folders,
            scope,
            loadAccounts = { services.readLater.accounts { behavior.behavior.readLater } },
        )
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

    /** Moves messages between folders and in and out of Read later. */
    val filing = MessageFiling(services, folders, scope)

    /** Moves, marks, and rules for messages shown by [feed]; [openRule] shows the rule editor for an account. */
    fun messageActions(
        feed: InboxFeedState,
        openRule: (accountId: String) -> Unit,
    ): MessageActions =
        MessageActions(
            moveTargets = filing::moveTargets,
            onMove = { message, target -> filing.move(feed, message, target) },
            canMark = ::canMark,
            onMark = { message, flag, on -> mark(feed, message, flag, on) },
            ruleForSender = { ruleForSender(it, openRule) },
            readLater = { filing.readLater(feed, it) },
            behavior = { behavior.behavior },
        )

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

    /** Starts a rule for the message's sender, where the account's server runs rules. */
    private fun ruleForSender(
        message: MailMessage,
        openRule: (accountId: String) -> Unit,
    ): (() -> Unit)? {
        val accountId = message.location.accountId
        if (rules.account(accountId)?.support == null) return null
        return {
            ruleEditor.startForSender(accountId, message.sender.address)
            openRule(accountId)
        }
    }

    /** Supplied messages have no server id, so only loaded mail can change its marks. */
    private fun canMark(
        message: MailMessage,
        flag: MessageFlag,
    ): Boolean = message.emailId.isNotEmpty() && folders.rightsOf(message).allow(flag)
}

private fun FolderRights.allow(flag: MessageFlag): Boolean =
    when (flag) {
        MessageFlag.Read -> maySetSeen
        MessageFlag.Flagged -> maySetKeywords
    }
