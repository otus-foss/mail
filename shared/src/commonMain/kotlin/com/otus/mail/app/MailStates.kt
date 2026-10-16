package com.otus.mail.app

import com.otus.accounts.presentation.AccountsState
import com.otus.avatar.AvatarOptions
import com.otus.mail.core.avatar.mailAvatarOptions
import com.otus.mail.feature.folders.domain.FolderRights
import com.otus.mail.feature.folders.domain.folderTree
import com.otus.mail.feature.folders.presentation.FolderSenders
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.folders.presentation.RuleEditorState
import com.otus.mail.feature.folders.presentation.RulesState
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.MessageHtml
import com.otus.mail.feature.inbox.domain.accountId
import com.otus.mail.feature.inbox.domain.mailboxName
import com.otus.mail.feature.inbox.presentation.ComposeState
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.MessageMoves
import com.otus.mail.feature.notifications.presentation.NotificationSettingsState
import kotlinx.coroutines.CoroutineScope

/** State holders that outlive a single page. One per composition of [App]. */
internal class MailStates(
    private val services: MailServices,
    private val scope: CoroutineScope,
    val behavior: MailBehaviorState = MailBehaviorState(),
) {
    val feed = InboxFeedState(services.mail, scope, services.avatars, ::avatarOptions)
    val folders =
        FoldersState(
            services.folders,
            scope,
            loadAccounts = { services.readLater.accounts { behavior.behavior.readLater } },
        )
    val rules = RulesState(services.rules, scope)

    /** Null where the platform has no instant pushes, which hides the Notifications page. */
    val notifications = services.instantPush?.let { NotificationSettingsState(it, scope) }

    /** Rules keep only addresses, so senders take their names from the inbox's loaded mail. */
    val folderSenders =
        FolderSenders(services.avatars, ::avatarOptions) { address ->
            feed.messages
                .firstOrNull { it.sender.address.equals(address, ignoreCase = true) }
                ?.sender
                ?.name
        }
    val ruleEditor = RuleEditorState(rules)
    val accounts =
        AccountsState(services.accounts, scope) {
            feed.refresh()
            folders.refresh()
            rules.refresh()
        }

    /**
     * A folder page loads only its own mail, in [pageScope], so leaving the page stops its loads and
     * avatar lookups. Avatars share the inbox's cache.
     */
    fun folderFeed(
        mailScope: MailScope,
        pageScope: CoroutineScope,
    ): InboxFeedState =
        InboxFeedState(services.mail, pageScope, services.avatars, ::avatarOptions, defaultScope = mailScope)

    /** Avatars load from outside the user's servers only while Website icons is on. */
    private fun avatarOptions(): AvatarOptions = mailAvatarOptions(behavior.behavior.websiteIcons)

    /** Every account's folder names in folder order, once each, for search to choose from. */
    fun mailboxNames(): List<String> =
        folders.accounts
            .flatMap { account -> account.folderTree().map { mailboxName(it.folder.name, it.folder.role) } }
            .distinct()

    /**
     * A draft for [request] from the account it names, or else the first one; null without
     * accounts. Reply all leaves out the account's sign-in and sending addresses.
     */
    fun compose(request: ComposeRequest): ComposeState? {
        val account =
            accounts.accounts.run { firstOrNull { it.id == request.accountId } ?: firstOrNull() } ?: return null
        val own = account.emailAddresses + account.username
        return ComposeState(services.drafts, scope, request, account.id, own)
    }

    /** Mail and folder counts change together, so a pull refreshes both. */
    fun refreshInbox() {
        feed.refresh()
        folders.refresh()
    }

    /** Moves messages between folders and in and out of Read later. */
    val filing = MessageFiling(services, folders, scope)
    private val cleaning = FolderCleaning(services, folders, scope)
    private val marking = MessageMarking(services, folders, scope)

    /** Rules that sort mail like a moved message, and the offer that follows a saved one. */
    val sorting = MessageSorting(rules, services.inboxMatcher, folders, filing, scope)

    /** Moves, marks, and rules for messages shown by [feed]; [openRule] shows the rule editor for an account. */
    fun messageActions(
        feed: InboxFeedState,
        openRule: (accountId: String) -> Unit,
    ): MessageActions =
        MessageActions(
            moves =
                MessageMoves(
                    targets = filing::moveTargets,
                    onMove = { message, target -> filing.move(feed, message, target) },
                    suggested = filing::suggestedTarget,
                    futureSorting = { sorting.futureSorting(it, feed) },
                ),
            canMark = ::canMark,
            onMark = { messages, flag, on -> marking.mark(feed, messages, flag, on) },
            ruleForSender = { ruleForSender(it, openRule) },
            readLater = { filing.readLater(feed, it) },
            behavior = { behavior.behavior },
            cleanup = cleaning.cleanup(feed),
        )

    /** The HTML body of an open message. */
    suspend fun messageHtml(message: MailMessage): MessageHtml? = services.bodies.html(message)

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
