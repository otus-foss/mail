package com.otus.mail.app

import com.otus.accounts.presentation.AccountsState
import com.otus.accounts.presentation.OwnAvatarsState
import com.otus.avatar.AvatarOptions
import com.otus.mail.core.avatar.mailAvatarOptions
import com.otus.mail.feature.folders.domain.FolderRights
import com.otus.mail.feature.folders.domain.folderTree
import com.otus.mail.feature.folders.presentation.FolderColorsState
import com.otus.mail.feature.folders.presentation.FolderNudgeState
import com.otus.mail.feature.folders.presentation.FolderRulesState
import com.otus.mail.feature.folders.presentation.FolderSenders
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.folders.presentation.components.folderActions
import com.otus.mail.feature.folders.presentation.forget
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.DevicePrivacy
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailScopeChoice
import com.otus.mail.feature.inbox.domain.MessageContent
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.accountId
import com.otus.mail.feature.inbox.domain.mailboxName
import com.otus.mail.feature.inbox.presentation.AddressPinsState
import com.otus.mail.feature.inbox.presentation.ComposeAccount
import com.otus.mail.feature.inbox.presentation.ComposeOrigin
import com.otus.mail.feature.inbox.presentation.ComposeServices
import com.otus.mail.feature.inbox.presentation.ComposeState
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.MessageMoves
import com.otus.mail.feature.inbox.presentation.SignaturesState
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

    val folders: FoldersState =
        FoldersState(
            services.folders,
            scope,
            loadCached = { services.readLater.cachedAccounts { behavior.behavior.readLater } },
            loadAccounts = { services.readLater.accounts { behavior.behavior.readLater } },
            onDeleted = {
                folderColors.forget(it)
                rules.forget(it)
            },
        )

    /** Colors the user gave their folders; the Folder colors switch hides them. */
    val folderColors: FolderColorsState =
        FolderColorsState(services.stores.folderColors, { folders.accounts }) { behavior.behavior.sidebar.folderColors }

    /** Which folder the color nudge offers a color for; only while folder colors and folder icons are on. */
    val folderNudge =
        FolderNudgeState(services.stores.folderNudges, folderColors, { folders.accounts }) {
            behavior.behavior.sidebar.folderColors && behavior.behavior.list.folderIcons
        }

    /** Each account's folder rules, kept in the Otus block of its active Sieve script. */
    val rules = FolderRulesState(services.rules, scope)

    /** Addresses pinned in "Show mail from". */
    val addressPins = AddressPinsState(services.stores.addressPins)

    /** The screen and keyboard protections Settings › Privacy offers on this platform. */
    val devicePrivacy: DevicePrivacy get() = services.devicePrivacy

    /** Null where the platform has no instant pushes, which hides the Notifications page. */
    val notifications =
        services.instantPush?.let { NotificationSettingsState(it, scope, services.stores.notificationPreviews) }

    /** Rules keep only addresses, so senders take their names from the inbox's loaded mail. */
    val folderSenders =
        FolderSenders(services.avatars, ::avatarOptions) { address ->
            feed.messages
                .firstOrNull { it.sender.address.equals(address, ignoreCase = true) }
                ?.sender
                ?.name
        }

    /** The avatars of the user's own addresses; a change redraws the mail that shows them. */
    val ownAvatars =
        OwnAvatarsState(services.ownAvatars(::avatarOptions), services.pickers.avatarPhotos, scope) { feed.refresh() }

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
    private fun avatarOptions(): AvatarOptions =
        mailAvatarOptions(behavior.behavior.websiteIcons, behavior.behavior.duckDuckGoIcons)

    /** Every account's folder names in folder order, once each, for search to choose from. */
    fun mailboxNames(): List<String> =
        folders.accounts
            .flatMap { account -> account.folderTree().map { mailboxName(it.folder.name, it.folder.role) } }
            .distinct()

    /**
     * A draft for [request] from the account it names, or else the first one; null without
     * accounts. Every account can send it. Reply all leaves out the account's sign-in and sending
     * addresses. Sending records the address it went from, which later drafts offer first.
     */
    fun compose(request: ComposeRequest): ComposeState? {
        val all = accounts.accounts
        val account = all.firstOrNull { it.id == request.accountId } ?: all.firstOrNull() ?: return null
        return ComposeState(
            ComposeServices(
                services.drafts,
                services.sender,
                services.addressBook,
                services.scheduled,
                services.threads,
                services.uploads,
                services.pickers.attachmentFiles,
            ),
            scope,
            request,
            ComposeOrigin(
                account.id,
                all.map { ComposeAccount(it.id, it.name) },
                account.emailAddresses + account.username,
                behavior.behavior.recentFrom,
                behavior.behavior.signatureInReplies,
            ),
            onSent = { from -> behavior.update { it.sentFrom(from.address) } },
        )
    }

    /** Every address's signature, for Settings › Signatures. */
    val signatures =
        SignaturesState(services.drafts, services.signatures, services.pickers.signatureImages, scope) {
            accounts.accounts.map { ComposeAccount(it.id, it.name) }
        }

    /** Mail and folder counts change together, so a pull refreshes both. */
    fun refreshInbox() {
        feed.refresh()
        folders.refresh()
    }

    /** Moves messages between folders and in and out of Read later. */
    val filing = MessageFiling(services, folders, folderColors, scope)

    /**
     * The toasts that offer to undo moves; they outlive the page a move closed, such as the open
     * message, and the activity, since the process holds them.
     */
    val moveToasts = MoveToasts(services.toasts)

    /** The Read view's services, folders, and privacy setting; its toasts join the move toasts. */
    val reading =
        ReadWiring(
            services,
            scope,
            behavior,
            folders,
            folderColors,
            moveToasts,
            folderActions(folders, folderColors, rules) { knownAddresses(it) },
        )

    /** Sends drafts after their page closes, with Undo and the outcome in a toast. */
    val outgoing =
        OutgoingMail(scope, moveToasts.toasts, services.scheduled) {
            behavior.behavior.undoSendSeconds *
                MILLIS_PER_SECOND
        }
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
                    listed = filing::listedTargets,
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

    /** Each page's applied filters while the app runs. */
    val pageFilters = PageFilters()

    /** The counts and people search of the filter panels. */
    val filterLookups = FilterLookups(services.mail, services.people) { mailScope.accountId?.let(::setOf) }

    /** The full body of an open message. */
    suspend fun messageContent(message: MailMessage): MessageContent? = services.bodies.content(message)

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

private const val MILLIS_PER_SECOND = 1_000L
