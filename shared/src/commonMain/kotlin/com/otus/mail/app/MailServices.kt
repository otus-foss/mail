package com.otus.mail.app

import com.otus.accounts.data.JmapAccountRepository
import com.otus.accounts.domain.AccountRepository
import com.otus.avatar.AvatarCacheConfig
import com.otus.avatar.AvatarOptions
import com.otus.avatar.AvatarService
import com.otus.avatar.AvatarStore
import com.otus.avatar.EmptyAvatarStore
import com.otus.avatar.HttpAvatarImageLoader
import com.otus.avatar.JmapContactAvatarSource
import com.otus.avatar.JmapOwnAvatars
import com.otus.avatar.PublicSuffixDomainResolver
import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.jmap.push.PushSubscriptions
import com.otus.mail.core.cache.AccountMailCache
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.cache.RecoveringMailCache
import com.otus.mail.core.mailbox.Mailboxes
import com.otus.mail.core.messages.data.EmailChangeQueue
import com.otus.mail.core.messages.data.JmapEmailWriter
import com.otus.mail.core.messages.data.ServerSites
import com.otus.mail.core.messages.domain.AddressBook
import com.otus.mail.core.messages.domain.DevicePrivacy
import com.otus.mail.core.messages.domain.DraftRepository
import com.otus.mail.core.messages.domain.DraftSender
import com.otus.mail.core.messages.domain.EmailWriter
import com.otus.mail.core.messages.domain.MessageBodies
import com.otus.mail.core.messages.domain.PeopleSearch
import com.otus.mail.core.messages.domain.ScheduledSends
import com.otus.mail.core.messages.domain.SenderBlocks
import com.otus.mail.core.messages.domain.SenderHistory
import com.otus.mail.core.messages.presentation.MoveToasts
import com.otus.mail.core.storage.DeviceStore
import com.otus.mail.feature.compose.data.JmapAttachmentUploads
import com.otus.mail.feature.compose.data.JmapComposeMail
import com.otus.mail.feature.compose.data.JmapSendChecker
import com.otus.mail.feature.compose.data.JmapSignatures
import com.otus.mail.feature.compose.data.JmapThreadHistory
import com.otus.mail.feature.compose.domain.AttachmentUploads
import com.otus.mail.feature.compose.domain.MailOutbox
import com.otus.mail.feature.compose.domain.SignatureRepository
import com.otus.mail.feature.compose.domain.ThreadHistory
import com.otus.mail.feature.contacts.data.JmapContactRepository
import com.otus.mail.feature.contacts.data.JmapMailTraces
import com.otus.mail.feature.contacts.domain.ContactRepository
import com.otus.mail.feature.contacts.domain.MailTraceSource
import com.otus.mail.feature.folders.data.JmapFolderRepository
import com.otus.mail.feature.folders.data.JmapFolderRuleRepository
import com.otus.mail.feature.folders.domain.FolderRepository
import com.otus.mail.feature.folders.domain.FolderRuleRepository
import com.otus.mail.feature.folders.domain.ReadLaterFolders
import com.otus.mail.feature.folders.domain.RuleSenderBlocks
import com.otus.mail.feature.inbox.data.JmapAddressBook
import com.otus.mail.feature.inbox.data.JmapMailSource
import com.otus.mail.feature.inbox.data.JmapPeopleSearch
import com.otus.mail.feature.inbox.data.JmapSenderHistory
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.notifications.data.JmapNewMailChecker
import com.otus.mail.feature.notifications.data.PushServices
import com.otus.mail.feature.notifications.domain.InstantPush
import com.otus.mail.feature.notifications.domain.MailPush
import com.otus.mail.feature.notifications.domain.NewMailChecker
import com.otus.mail.feature.read.data.AttachmentCache
import com.otus.mail.feature.read.data.AttachmentRetention
import com.otus.mail.feature.read.data.JmapAttachmentDownloads
import com.otus.mail.feature.read.data.JmapConversationMessages
import com.otus.mail.feature.read.data.JmapKnownPeople
import com.otus.mail.feature.read.data.JmapMessageBodies
import com.otus.mail.feature.read.data.JmapMessageHeaders
import com.otus.mail.feature.read.data.JmapNewsletters
import com.otus.mail.feature.read.domain.ConversationMessages
import com.otus.mail.feature.read.domain.KnownPeople
import com.otus.mail.feature.read.domain.MessageHeaders
import com.otus.mail.feature.read.domain.Newsletters
import com.otus.mail.feature.read.presentation.NewsletterSteps
import com.otus.storage.StringMapStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Connects data implementations to feature contracts. Create one per process: it owns the HTTP
 * client. Platform entry points supply their protected [LoginStore] and app-private stores.
 */
class MailServices(
    private val logins: LoginStore = InMemoryLoginStore(),
    avatarStore: AvatarStore = EmptyAvatarStore,
    internal val stores: MailDeviceStores = MailDeviceStores(),
    /** Instant pushes through the selected service; null where the platform has none. */
    internal val instantPush: InstantPush? = null,
    internal val pickers: MailPickers = MailPickers(),
    private val host: MailPlatform = MailPlatform(),
    createClient: () -> JmapClient = { JmapClient() },
) : PushServices {
    internal val nightly: NightlyUpdates get() = host.nightly

    /** The screen and keyboard protections this platform can apply. */
    internal val devicePrivacy: DevicePrivacy get() = host.devicePrivacy

    /** Pushed changes, which reload the open inbox and folders while Mail is in [visibility]. */
    val serverChanges = ServerChanges()

    /** Whether Mail is on screen; the platform reports it. */
    val visibility = MailVisibility()

    /** The device's copy of each login's mail; see docs/storage.md. */
    private val accountCache = AccountMailCache(RecoveringMailCache(stores.cache))
    internal val cache: MailCache get() = accountCache

    /** Each login's folders, which every feature reads from the cache. */
    private val mailboxes by lazy { Mailboxes(cache) }
    private val client by lazy(createClient)
    private val connections by lazy { JmapConnections(client, logins) }
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
        JmapAccountRepository(
            client,
            connections,
            logins,
            MailAccountService,
            listOf(DeviceAccountData(stores, outbox, instantPush), changes, accountCache, retention, avatars),
        )
    }

    /** Marks and moves that wait for the server; see docs/storage.md. */
    private val changes by lazy { EmailChangeQueue(connections, cache, stores.waiting.changes, host.wakeOutbox) }
    private val jmapWriter by lazy { JmapEmailWriter(connections, cache, changes, mailboxes) }

    /** Every change to emails, also for a notification's buttons, which act without opening Mail. */
    val writer: EmailWriter get() = jmapWriter
    internal val mail: MailSource by lazy {
        JmapMailSource(connections, cache, sites = serverSites, queue = changes)
    }
    internal val bodies: MessageBodies by lazy { JmapMessageBodies(connections, cache) }
    private val compose by lazy { JmapComposeMail(connections, mailboxes) }
    internal val drafts: DraftRepository get() = compose.drafts
    internal val sender: DraftSender get() = compose.sender
    internal val scheduled: ScheduledSends get() = compose.sender

    /** Messages handed over for sending, kept until the server has them; see docs/compose.md. */
    internal val outbox by lazy {
        MailOutbox(stores.waiting.outbox, compose.sender, JmapSendChecker(connections), host.wakeOutbox)
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
    internal val folders: FolderRepository by lazy { JmapFolderRepository(connections, cache) }
    internal val conversationMessages: ConversationMessages by lazy {
        JmapConversationMessages(connections, cache, sites = serverSites)
    }

    /** Attachments streamed to the device, the ones kept offline, and when the others go; see docs/storage.md. */
    internal val retention by lazy { AttachmentRetention(stores.downloads) }
    internal val attachments by lazy {
        AttachmentCache(JmapAttachmentDownloads(connections), stores.downloads, retention)
    }
    internal val knownPeople: KnownPeople by lazy { JmapKnownPeople(connections, mailboxes) }
    internal val newsletters: Newsletters by lazy {
        JmapNewsletters(connections, drafts, sender, jmapWriter, mailboxes)
    }
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
    internal val rules: FolderRuleRepository by lazy { JmapFolderRuleRepository(connections, mailboxes) }

    /** Finds mail that arrived since the check that last wrote [states], for notifications. */
    fun newMail(states: DeviceStore<String, String>): NewMailChecker =
        JmapNewMailChecker(connections, mailboxes, states)

    /** Create one per process. */
    override fun pushSubscriptions(
        states: StringMapStore,
        deviceClientId: String,
        push: MailPush,
        beforeCreate: suspend (String) -> Unit,
    ): PushSubscriptions =
        PushSubscriptions(
            connections,
            logins,
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
