package com.otus.mail.app

import com.otus.accounts.domain.ownAddresses
import com.otus.accounts.presentation.AccountsState
import com.otus.avatar.AvatarOptions
import com.otus.avatar.avatarOptions
import com.otus.avatar.ui.presentation.OwnAvatarsState
import com.otus.dateformat.DateFormatter
import com.otus.mail.core.mail.EmailAddressKey
import com.otus.mail.core.mailbox.folderTree
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.DevicePrivacy
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.messages.domain.MailScopeChoice
import com.otus.mail.core.messages.domain.MailboxScope
import com.otus.mail.core.messages.domain.MessageContent
import com.otus.mail.core.messages.presentation.MailEdits
import com.otus.mail.core.messages.presentation.MessageActions
import com.otus.mail.core.messages.presentation.MessageMarking
import com.otus.mail.core.messages.presentation.MessageMoves
import com.otus.mail.core.messages.presentation.MoveToasts
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.feature.contacts.presentation.ContactAvatarUpdates
import com.otus.mail.feature.contacts.presentation.ContactPickers
import com.otus.mail.feature.contacts.presentation.ContactSync
import com.otus.mail.feature.contacts.presentation.ContactsState
import com.otus.mail.feature.contacts.presentation.FrequentContacts
import com.otus.mail.feature.folders.presentation.FolderColorsState
import com.otus.mail.feature.folders.presentation.FolderNudgeState
import com.otus.mail.feature.folders.presentation.FolderRulesState
import com.otus.mail.feature.folders.presentation.FolderSenders
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.folders.presentation.components.folderActions
import com.otus.mail.feature.folders.presentation.forget
import com.otus.mail.feature.inbox.domain.accountId
import com.otus.mail.feature.inbox.presentation.AddressPinsState
import com.otus.mail.feature.inbox.presentation.ComposeAccount
import com.otus.mail.feature.inbox.presentation.ComposeOrigin
import com.otus.mail.feature.inbox.presentation.ComposeServices
import com.otus.mail.feature.inbox.presentation.ComposeState
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.SignaturesState
import com.otus.mail.feature.notifications.presentation.NotificationSettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** State holders that outlive a single page. One per composition of [App]. */
internal class MailStates(
    private val services: MailServices,
    private val scope: CoroutineScope,
    val behavior: MailBehaviorState = MailBehaviorState(),
    private val chosenScope: () -> MailScopeChoice = { MailScopeChoice.All },
    /** Chooses whose mail the pages show, as the sidebar and the inbox's account switcher do. */
    val chooseScope: (MailScopeChoice) -> Unit = {},
    /** The user's date format now, for dates in notices and toasts. */
    private val dates: () -> DateFormatter,
) {
    /** Whose mail the pages show. A removed account's choice falls back to every account. */
    val mailScope: MailScopeChoice
        get() = chosenScope().within(accounts.accounts.map { it.id })

    /** The changes the user makes to mail, which every list and Read view shows at once. */
    val edits = MailEdits(services.writer, scope, services.moveToasts, afterUndo = services.serverChanges::changed)

    val feed = feedOf(behavior.behavior.inbox.mailScope(), scope)

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

    init {
        // Folder counts follow every saved change of mail.
        scope.launch { edits.changed.collect { folders.refresh() } }
    }

    /** Colors the user gave their folders; the Folder colors switch hides them. */
    val folderColors: FolderColorsState =
        FolderColorsState(
            services.stores.folders.colors,
            { folders.accounts },
        ) { behavior.behavior.sidebar.folderColors }

    /** Which folder the color nudge offers a color for; only while folder colors and folder icons are on. */
    val folderNudge =
        FolderNudgeState(services.stores.folders.nudges, folderColors, { folders.accounts }) {
            behavior.behavior.sidebar.folderColors && behavior.behavior.list.folderIcons
        }

    /** Each account's folder rules, kept in the Otus block of its active Sieve script. */
    val rules = FolderRulesState(services.rules, scope)

    /** The contact photos sender avatars take from the device's contact list. */
    val contactAvatars =
        ContactAvatarUpdates(services.avatars, services.work, { behavior.behavior.allAccountPhotos }) { feed.refresh() }

    /** Every login's contacts and the contact changes waiting for the server. */
    val contacts: ContactsState =
        ContactsState(
            services.contacts,
            ContactSync(services.contacts, services.stores.contacts, services.work),
            services.work,
            ContactPickers(
                services.pickers.attachmentFiles?.let { picker ->
                    services.pickers.savedFiles?.let { VCardFiles(picker, it) }
                },
                services.pickers.avatarPhotos,
            ),
            FrequentContacts(services.mailTraces, services.stores.contacts, services.work),
            contactAvatars,
            ownAddresses = {
                accounts.accounts
                    .flatMap { it.ownAddresses }
                    .map { EmailAddressKey(it) }
                    .toSet()
            },
        )

    /** Addresses pinned in "Show mail from". */
    val addressPins = AddressPinsState(services.stores.addressPins)

    /** The screen and keyboard protections Settings › Privacy & security offers on this platform. */
    val devicePrivacy: DevicePrivacy get() = services.devicePrivacy

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
            contacts.sync.reload()
            contacts.refresh()
            folders.refresh()
            rules.refresh()
            folderColors.reload()
            addressPins.reload()
        }

    /** Null where the platform has no instant pushes, which hides the Notifications page. */
    val notifications =
        services.instantPush?.let {
            NotificationSettingsState(it, scope, services.stores.notificationPreviews) { accounts.accounts }
        }

    /**
     * The mail of [mailScope], loaded in [pageScope], so leaving a folder page stops its loads and
     * avatar lookups. It shows edits until then. Avatars share the inbox's cache.
     */
    fun feedOf(
        mailScope: MailScope,
        pageScope: CoroutineScope,
    ): InboxFeedState {
        val feed = InboxFeedState(services.mail, pageScope, services.avatars, ::avatarOptions, mailScope, dates)
        val stop = edits.observe(feed.edits)
        pageScope.coroutineContext.job.invokeOnCompletion { stop() }
        return feed
    }

    /** Avatars load from outside the user's servers only while Website icons is on. */
    internal fun avatarOptions(): AvatarOptions =
        behavior.behavior.let { avatarOptions(it.websiteIcons, it.iconSource, it.bulwarkAddress) }

    /** Every account's folders in folder order, once each by role or name, for search to choose from. */
    fun searchMailboxes(): List<MailboxScope> =
        folders.accounts
            .flatMap { account -> account.folderTree().map { it.folder } }
            .map { MailboxScope(null, it.role?.title ?: it.mailboxName, it.role) }
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
                services.addressBook,
                services.scheduled,
                services.threads,
                services.uploads,
                services.pickers.attachmentFiles,
            ),
            // The process's scope, so saves and a discarded copy's deletion outlive a recreated activity.
            services.work,
            request,
            ComposeOrigin(
                account.id,
                all.map { ComposeAccount(it.id, it.name) },
                account.emailAddresses + account.username,
                behavior.behavior.recentFrom,
                behavior.behavior.signatureInReplies,
                photoCleaning = { behavior.behavior.photoCleaning },
                ownAvatar = { accountId, address ->
                    all.firstOrNull { it.id == accountId }?.let { ownAvatars.avatar(it.own, address) }
                },
            ),
            onSent = { from -> behavior.update { it.sentFrom(from.address) } },
        )
    }

    /** Every address's signature, for Settings › Mail behavior › Signatures. */
    val signatures =
        SignaturesState(services.drafts, services.signatures, services.pickers.signatureImages, scope) {
            accounts.accounts.map { ComposeAccount(it.id, it.name) }
        }

    /** Mail and folder counts change together, so a pull refreshes both. */
    fun refreshInbox() {
        feed.refresh()
        folders.refresh()
    }

    val filing = MessageFiling(services.senderHistory, folders, folderColors, edits)

    /** The toasts that offer to undo moves; the process holds them, see [MailServices.work]. */
    val moveToasts: MoveToasts get() = services.moveToasts

    /** Pages that work outliving this composition opens, such as a toast's, and what the platform opens. */
    val requests: MailRequests get() = services.requests

    /** The Read view's services, folders, and privacy setting; its toasts join the move toasts. */
    val reading =
        ReadWiring(
            services,
            behavior,
            folders,
            folderColors,
            edits,
            folderActions(folders, folderColors, rules) { knownAddresses(it) },
        )

    /**
     * Sends drafts after their page closes, with Undo and the outcome in a toast. It runs in the
     * process's scope, so a send outlives a recreated activity.
     */
    val outgoing =
        OutgoingMail(services.work, moveToasts.toasts, services.scheduled, services.outbox, dates) {
            behavior.behavior.undoSendSeconds.seconds.inWholeMilliseconds
        }
    private val cleaning = FolderCleaning(folders, edits)
    private val marking = MessageMarking(edits)

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
                    onMove = { message, target -> filing.move(feed, message, target) },
                    suggested = filing::suggestedTarget,
                    futureSorting = { sorting.futureSorting(it, feed) },
                ),
            marks = marking.marks(feed, folders::mayMark),
            ruleForSender = senderRules::start,
            readLater = { filing.readLater(feed, it) },
            behavior = { behavior.behavior },
            cleanup = cleaning.cleanup(feed),
        )

    /** Each page's applied filters while the app runs. */
    val pageFilters = PageFilters()

    /** The counts and people search of the filter panels. */
    val filterLookups = FilterLookups(services.mail, services.people) { mailScope.accountId?.let(::setOf) }

    suspend fun messageContent(message: MailMessage): MessageContent? = services.bodies.content(message)
}
