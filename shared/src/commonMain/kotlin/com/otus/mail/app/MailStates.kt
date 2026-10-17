package com.otus.mail.app

import com.otus.accounts.presentation.AccountsState
import com.otus.avatar.AvatarOptions
import com.otus.mail.core.avatar.mailAvatarOptions
import com.otus.mail.feature.folders.domain.FolderRights
import com.otus.mail.feature.folders.domain.folderTree
import com.otus.mail.feature.folders.presentation.FolderColorsState
import com.otus.mail.feature.folders.presentation.FolderRulesState
import com.otus.mail.feature.folders.presentation.FolderSenders
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.folders.presentation.forget
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.InboxContents
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailScopeChoice
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.MessageHtml
import com.otus.mail.feature.inbox.domain.accountId
import com.otus.mail.feature.inbox.domain.mailboxName
import com.otus.mail.feature.inbox.presentation.AddressPinsState
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
    private val chosenScope: () -> MailScopeChoice = { MailScopeChoice.All },
) {
    /** Whose mail the pages show. A removed account's choice falls back to every account. */
    val mailScope: MailScopeChoice
        get() = chosenScope().within(accounts.accounts.map { it.id })

    val feed =
        InboxFeedState(services.mail, scope, services.avatars, ::avatarOptions, behavior.behavior.inbox.mailScope())

    /** What the Inbox page lists; addresses of removed accounts drop out. */
    val inboxContents: InboxContents
        get() = behavior.behavior.inbox.within(accounts.accounts.map { it.id })
    val folders: FoldersState =
        FoldersState(
            services.folders,
            scope,
            loadAccounts = { services.readLater.accounts { behavior.behavior.readLater } },
            onDeleted = {
                folderColors.forget(it)
                rules.forget(it)
            },
        )

    /** Colors the user gave their folders; the Folder colors switch hides them. */
    val folderColors: FolderColorsState =
        FolderColorsState(services.stores.folderColors, { folders.accounts }) { behavior.behavior.sidebar.folderColors }

    /** Each account's folder rules, kept in the Otus block of its active Sieve script. */
    val rules = FolderRulesState(services.rules, scope)

    /** Addresses pinned in "Show mail from". */
    val addressPins = AddressPinsState(services.stores.addressPins)

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
    val accounts =
        AccountsState(services.accounts, scope) {
            feed.refresh()
            folders.refresh()
            rules.refresh()
            folderColors.reload()
            addressPins.reload()
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
    val filing = MessageFiling(services, folders, folderColors, scope)

    /** The toasts that offer to undo moves; they outlive the page a move closed, such as the open message. */
    val moveToasts = MoveToasts()
    private val cleaning = FolderCleaning(services, folders, scope)
    private val marking = MessageMarking(services, folders, scope)

    /** Rules that sort mail like a moved message, and the offer that follows a saved one. */
    val sorting = MessageSorting(rules, services.rules, folders, filing, scope)

    /** "Rule for sender": a folder, then the folder's Add rule sheet. */
    val senderRules = SenderRules(rules, folders)

    /** Moves, marks, and rules for messages shown by [feed]. */
    fun messageActions(feed: InboxFeedState): MessageActions =
        MessageActions(
            moves =
                MessageMoves(
                    targets = filing::moveTargets,
                    onMove = { message, target ->
                        val move = filing.move(feed, message, target)
                        moveToasts.moved(target) { filing.moveBack(feed, message, target, after = move) }
                    },
                    suggested = filing::suggestedTarget,
                    futureSorting = { sorting.futureSorting(it, feed) },
                ),
            canMark = ::canMark,
            onMark = { messages, flag, on -> marking.mark(feed, messages, flag, on) },
            ruleForSender = senderRules::start,
            readLater = { filing.readLater(feed, it) },
            behavior = { behavior.behavior },
            cleanup = cleaning.cleanup(feed),
        )

    /** The HTML body of an open message. */
    suspend fun messageHtml(message: MailMessage): MessageHtml? = services.bodies.html(message)

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
