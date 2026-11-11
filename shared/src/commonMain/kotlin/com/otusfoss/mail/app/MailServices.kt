package com.otusfoss.mail.app

import com.otusfoss.core.accounts.data.JmapAccountRepository
import com.otusfoss.core.accounts.domain.AccountRepository
import com.otusfoss.core.avatar.AvatarCacheConfig
import com.otusfoss.core.avatar.AvatarOptions
import com.otusfoss.core.avatar.AvatarService
import com.otusfoss.core.avatar.AvatarStore
import com.otusfoss.core.avatar.EmptyAvatarStore
import com.otusfoss.core.avatar.HttpAvatarImageLoader
import com.otusfoss.core.avatar.JmapContactAvatarSource
import com.otusfoss.core.avatar.JmapOwnAvatars
import com.otusfoss.core.avatar.PublicSuffixDomainResolver
import com.otusfoss.core.designsystem.shared.feedback.components.ToastState
import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.core.jmap.login.JmapLogin
import com.otusfoss.core.jmap.login.LoginStore
import com.otusfoss.core.jmap.login.LoginStoreException
import com.otusfoss.core.jmap.push.PushSubscriptions
import com.otusfoss.core.storage.StringMapStore
import com.otusfoss.mail.core.cache.AccountMailCache
import com.otusfoss.mail.core.cache.MailCache
import com.otusfoss.mail.core.cache.RecoveringMailCache
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.core.messages.data.EmailChangeQueue
import com.otusfoss.mail.core.messages.data.JmapEmailByIdWriter
import com.otusfoss.mail.core.messages.data.JmapEmailWriter
import com.otusfoss.mail.core.messages.data.ServerSites
import com.otusfoss.mail.core.messages.domain.AddressBook
import com.otusfoss.mail.core.messages.domain.DevicePrivacy
import com.otusfoss.mail.core.messages.domain.DraftRepository
import com.otusfoss.mail.core.messages.domain.DraftSender
import com.otusfoss.mail.core.messages.domain.EmailByIdWriter
import com.otusfoss.mail.core.messages.domain.EmailWriter
import com.otusfoss.mail.core.messages.domain.MessageBodies
import com.otusfoss.mail.core.messages.domain.PeopleSearch
import com.otusfoss.mail.core.messages.domain.ScheduledSends
import com.otusfoss.mail.core.messages.domain.SenderBlocks
import com.otusfoss.mail.core.messages.presentation.MoveToasts
import com.otusfoss.mail.feature.compose.data.JmapAttachmentUploads
import com.otusfoss.mail.feature.compose.data.JmapComposeMail
import com.otusfoss.mail.feature.compose.data.JmapSendChecker
import com.otusfoss.mail.feature.compose.data.JmapSignatures
import com.otusfoss.mail.feature.compose.data.JmapThreadHistory
import com.otusfoss.mail.feature.compose.domain.AttachmentUploads
import com.otusfoss.mail.feature.compose.domain.MailOutbox
import com.otusfoss.mail.feature.compose.domain.SavedDrafts
import com.otusfoss.mail.feature.compose.domain.SignatureRepository
import com.otusfoss.mail.feature.compose.domain.ThreadHistory
import com.otusfoss.mail.feature.contacts.data.JmapContactRepository
import com.otusfoss.mail.feature.contacts.data.JmapMailTraces
import com.otusfoss.mail.feature.contacts.domain.ContactRepository
import com.otusfoss.mail.feature.contacts.domain.MailTraceSource
import com.otusfoss.mail.feature.folders.data.JmapFolderRepository
import com.otusfoss.mail.feature.folders.data.JmapFolderRuleRepository
import com.otusfoss.mail.feature.folders.data.JmapSenderHistory
import com.otusfoss.mail.feature.folders.domain.FolderRepository
import com.otusfoss.mail.feature.folders.domain.FolderRuleRepository
import com.otusfoss.mail.feature.folders.domain.ReadLaterFolders
import com.otusfoss.mail.feature.folders.domain.RuleSenderBlocks
import com.otusfoss.mail.feature.folders.domain.SenderHistory
import com.otusfoss.mail.feature.inbox.data.CategorizedMailSource
import com.otusfoss.mail.feature.inbox.data.CategoryRecords
import com.otusfoss.mail.feature.inbox.data.JmapAddressBook
import com.otusfoss.mail.feature.inbox.data.JmapMailSource
import com.otusfoss.mail.feature.inbox.data.JmapPeopleSearch
import com.otusfoss.mail.feature.inbox.domain.MailSource
import com.otusfoss.mail.feature.inbox.domain.category.SenderDirectory
import com.otusfoss.mail.feature.notifications.data.JmapNewMailChecker
import com.otusfoss.mail.feature.notifications.data.PushServices
import com.otusfoss.mail.feature.notifications.domain.InstantPush
import com.otusfoss.mail.feature.notifications.domain.MailPush
import com.otusfoss.mail.feature.notifications.domain.NewMailChecker
import com.otusfoss.mail.feature.read.data.AttachmentCache
import com.otusfoss.mail.feature.read.data.AttachmentRetention
import com.otusfoss.mail.feature.read.data.JmapAttachmentDownloads
import com.otusfoss.mail.feature.read.data.JmapConversationMessages
import com.otusfoss.mail.feature.read.data.JmapKnownPeople
import com.otusfoss.mail.feature.read.data.JmapMessageBodies
import com.otusfoss.mail.feature.read.data.JmapMessageHeaders
import com.otusfoss.mail.feature.read.data.JmapNewsletters
import com.otusfoss.mail.feature.read.data.JmapSenderMails
import com.otusfoss.mail.feature.read.domain.ConversationMessages
import com.otusfoss.mail.feature.read.domain.KnownPeople
import com.otusfoss.mail.feature.read.domain.MessageHeaders
import com.otusfoss.mail.feature.read.domain.Newsletters
import com.otusfoss.mail.feature.read.domain.SenderMails
import com.otusfoss.mail.feature.read.presentation.NewsletterSteps
import com.otusfoss.mail.resources.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

/**
 * Connects data implementations to feature contracts. Create one per process: it owns the HTTP
 * client. Platform entry points supply their protected [LoginStore] and app-private stores.
 */
class MailServices(
    private val logins: LoginStore = InMemoryLoginStore(),
    avatarStore: AvatarStore = EmptyAvatarStore,
    internal val stores: MailDeviceStores = MailDeviceStores(),
    internal val pickers: MailPickers = MailPickers(),
    private val host: MailPlatform = MailPlatform(),
    /** Where saved logins and waiting changes are read and written, off the main thread. */
    private val io: CoroutineContext = Dispatchers.IO,
    createClient: () -> JmapClient = { JmapClient() },
) : PushServices {
    internal val nightly: NightlyUpdates get() = host.nightly

    internal val instantPush: InstantPush? get() = host.instantPush

    /** The screen and keyboard protections this platform can apply. */
    internal val devicePrivacy: DevicePrivacy get() = host.devicePrivacy

    /** Pushed changes, which reload the open inbox and folders while Mail is in [visibility]. */
    val serverChanges = ServerChanges()

    /** Whether Mail is on screen; the platform reports it. */
    val visibility = MailVisibility()

    /** The device's copy of each login's mail; see docs/storage.md. */
    private val accountCache = AccountMailCache(RecoveringMailCache(stores.cache))
    internal val cache: MailCache get() = accountCache

    /** Each login's folders, the one keeper of them in the cache, which every feature reads. */
    private val mailboxes by lazy { Mailboxes(cache) }
    private val client by lazy(createClient)
    private val connections by lazy { JmapConnections(client, logins, io) }
    private val avatarImages by lazy { HttpAvatarImageLoader() }
    private val domains by lazy { PublicSuffixDomainResolver() }
    private val serverSites by lazy { ServerSites(domains) }
    internal val avatars by lazy {
        AvatarService(
            JmapContactAvatarSource(connections, avatarImages),
            avatarImages,
            domains = domains,
            cacheConfig = AvatarCacheConfig(store = avatarStore),
        )
    }

    /** The avatar of each of the user's own addresses, read with [options] like any sender's. */
    internal fun ownAvatars(options: () -> AvatarOptions): JmapOwnAvatars =
        JmapOwnAvatars(connections, avatars, options)

    internal val accounts: AccountRepository by lazy {
        val device = DeviceAccountData(stores, outbox, instantPush, host.alerts)
        JmapAccountRepository(
            client,
            connections,
            logins,
            MailAccountService,
            listOf(device, changes, accountCache, retention, avatars),
        )
    }

    /** Marks and moves that wait for the server; see docs/storage.md. */
    private val changes by lazy {
        EmailChangeQueue(connections, cache, stores.waiting.changes, host.wakeOutbox, io = io)
    }

    /** Every change to emails, also for a notification's buttons, which act without opening Mail. */
    val writer: EmailWriter by lazy { JmapEmailWriter(connections, cache, changes) }

    /** The changes a notification's buttons make to one email. */
    val byIdWriter: EmailByIdWriter by lazy { JmapEmailByIdWriter(connections, changes, mailboxes) }
    internal val mail: MailSource by lazy {
        CategorizedMailSource(
            JmapMailSource(connections, cache, changes, mailboxes, sites = serverSites),
            cache,
            CategoryRecords(::senderCategories, host.categoriesOn),
        )
    }
    internal val bodies: MessageBodies by lazy { JmapMessageBodies(connections, cache) }
    private val compose by lazy { JmapComposeMail(connections, mailboxes, changes) }
    internal val drafts: DraftRepository get() = compose.drafts
    internal val sender: DraftSender get() = compose.sender
    internal val scheduled: ScheduledSends get() = compose.sender
    internal val savedDrafts: SavedDrafts get() = compose.saved

    /** Messages handed over for sending, kept until the server has them; see docs/compose.md. */
    internal val outbox by lazy {
        val checker = JmapSendChecker(connections, changes)
        MailOutbox(stores.waiting.outbox, compose.sender, checker, host.wakeOutbox, io = io)
    }
    internal val signatures: SignatureRepository by lazy { JmapSignatures(connections) }
    internal val threads: ThreadHistory by lazy { JmapThreadHistory(connections) }
    internal val uploads: AttachmentUploads by lazy { JmapAttachmentUploads(connections) }
    internal val addressBook: AddressBook by lazy { JmapAddressBook(connections) }
    internal val contacts: ContactRepository by lazy { JmapContactRepository(connections, cache) }

    /** Each login's recent mail, for the people the user deals with most. */
    internal val mailTraces: MailTraceSource by lazy { JmapMailTraces(connections, mailboxes) }
    internal val people: PeopleSearch by lazy { JmapPeopleSearch(connections, addressBook) }
    internal val senderHistory: SenderHistory by lazy { JmapSenderHistory(connections) }
    internal val folders: FolderRepository by lazy { JmapFolderRepository(connections, mailboxes) }
    internal val conversationMessages: ConversationMessages by lazy {
        JmapConversationMessages(connections, cache, mailboxes, sites = serverSites)
    }

    /** Attachments streamed to the device, the ones kept offline, and when the others go; see docs/storage.md. */
    internal val retention by lazy { AttachmentRetention(stores.downloads) }
    internal val attachments by lazy {
        AttachmentCache(JmapAttachmentDownloads(connections), stores.downloads, retention)
    }
    internal val knownPeople: KnownPeople by lazy { JmapKnownPeople(connections, mailboxes) }
    internal val newsletters: Newsletters by lazy {
        JmapNewsletters(connections, drafts, sender, writer, mailboxes)
    }
    internal val senderMails: SenderMails by lazy { JmapSenderMails(connections, mailboxes) }
    internal val headers: MessageHeaders by lazy { JmapMessageHeaders(connections) }
    internal val blocks: SenderBlocks by lazy { RuleSenderBlocks(rules, folders) }

    /**
     * Toasts and the work their actions and closing start, such as an unsubscribe sent once its Undo
     * window ends. They belong to the process, so a toast shown before the activity was recreated, as
     * on rotation, still shows and still finishes its work. Pages it opens go through [requests].
     */
    internal val work: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    internal val moveToasts by lazy { MoveToasts(ToastState(), work) }
    val requests by lazy { MailRequests(work) }

    /** Each newsletter strip's step while the process runs, so a reopened message shows it. */
    internal val newsletterSteps = NewsletterSteps()

    /** Lives as long as the process, so its lock also covers the loads of a recreated activity. */
    internal val readLater by lazy { ReadLaterFolders(folders, stores.folders.readLater) }
    internal val rules: FolderRuleRepository by lazy { JmapFolderRuleRepository(connections, mailboxes, changes) }

    /** Finds mail that arrived since the last check, for notifications, on a platform that keeps their state. */
    fun newMail(): NewMailChecker = JmapNewMailChecker(connections, mailboxes, checkNotNull(stores.alerts.mailStates))

    /** Create one per process. */
    override fun pushSubscriptions(
        states: StringMapStore,
        deviceClientId: String,
        push: MailPush,
        beforeCreate: suspend (String) -> Unit,
    ): PushSubscriptions =
        PushSubscriptions(
            connections,
            states,
            deviceClientId,
            push.types,
            beforeCreate = beforeCreate,
        )

    /**
     * Sends the messages the outbox kept that are due, and the marks and moves that wait, as from
     * a background job after the app closed. True when some still wait for the network, so the job
     * should run again.
     */
    suspend fun resumeOutbox(): Boolean {
        outbox.resume()
        val changesWait = changes.flushAll()
        return outbox.waiting() || changesWait
    }

    /** The server signing key used to register directly with Google (RFC 9749). */
    suspend fun pushVapidKey(login: JmapLogin): String? = connections.connection(login).session().vapidPublicKey

    override fun notificationLogins(): List<JmapLogin> = logins.logins()

    /** Call it off the main thread. */
    override fun logins(): List<JmapLogin> =
        try {
            logins.logins()
        } catch (_: LoginStoreException) {
            emptyList()
        }
}

/** The curated sender list Inbox categories start from; see docs/categories.md. */
private suspend fun senderCategories(): SenderDirectory =
    SenderDirectory.parse(Res.readBytes("files/sender_categories.txt").decodeToString())
