package com.otus.mail.app

import com.otus.accounts.data.JmapAccountRepository
import com.otus.accounts.domain.AccountRepository
import com.otus.accounts.domain.AccountService
import com.otus.accounts.domain.AccountStorageException
import com.otus.avatar.AvatarCacheConfig
import com.otus.avatar.AvatarOptions
import com.otus.avatar.AvatarService
import com.otus.avatar.AvatarStore
import com.otus.avatar.DomainIconSource
import com.otus.avatar.EmptyAvatarStore
import com.otus.avatar.HttpAvatarImageLoader
import com.otus.avatar.JmapContactAvatarSource
import com.otus.avatar.JmapOwnAvatars
import com.otus.avatar.PublicSuffixDomainResolver
import com.otus.designsystem.components.ToastState
import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.cache.AccountMailCache
import com.otus.mail.core.cache.CacheKind
import com.otus.mail.core.cache.CacheStorageException
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.cache.RecoveringMailCache
import com.otus.mail.designsystem.resources.Res
import com.otus.mail.feature.contacts.data.JmapContactRepository
import com.otus.mail.feature.contacts.data.JmapMailTraces
import com.otus.mail.feature.contacts.domain.ContactRepository
import com.otus.mail.feature.contacts.domain.MailTraceSource
import com.otus.mail.feature.contacts.domain.forgetAccount
import com.otus.mail.feature.folders.data.JmapFolderRepository
import com.otus.mail.feature.folders.data.JmapFolderRuleRepository
import com.otus.mail.feature.folders.domain.FolderRepository
import com.otus.mail.feature.folders.domain.FolderRuleRepository
import com.otus.mail.feature.folders.domain.ReadLaterFolders
import com.otus.mail.feature.folders.domain.ReadLaterStore
import com.otus.mail.feature.folders.domain.forgetAccount
import com.otus.mail.feature.inbox.data.JmapAddressBook
import com.otus.mail.feature.inbox.data.JmapAttachmentDownloads
import com.otus.mail.feature.inbox.data.JmapAttachmentUploads
import com.otus.mail.feature.inbox.data.JmapConversationMessages
import com.otus.mail.feature.inbox.data.JmapConversationMute
import com.otus.mail.feature.inbox.data.JmapDraftRepository
import com.otus.mail.feature.inbox.data.JmapDraftSender
import com.otus.mail.feature.inbox.data.JmapKnownPeople
import com.otus.mail.feature.inbox.data.JmapMailSource
import com.otus.mail.feature.inbox.data.JmapMessageBodies
import com.otus.mail.feature.inbox.data.JmapMessageCleaner
import com.otus.mail.feature.inbox.data.JmapMessageHeaders
import com.otus.mail.feature.inbox.data.JmapMessageMarker
import com.otus.mail.feature.inbox.data.JmapMessageMover
import com.otus.mail.feature.inbox.data.JmapNewsletters
import com.otus.mail.feature.inbox.data.JmapPeopleSearch
import com.otus.mail.feature.inbox.data.JmapSenderHistory
import com.otus.mail.feature.inbox.data.JmapSignatures
import com.otus.mail.feature.inbox.data.JmapThreadHistory
import com.otus.mail.feature.inbox.domain.AddressBook
import com.otus.mail.feature.inbox.domain.AttachmentDownloads
import com.otus.mail.feature.inbox.domain.AttachmentUploads
import com.otus.mail.feature.inbox.domain.ConversationMessages
import com.otus.mail.feature.inbox.domain.ConversationMute
import com.otus.mail.feature.inbox.domain.DevicePrivacy
import com.otus.mail.feature.inbox.domain.DraftRepository
import com.otus.mail.feature.inbox.domain.DraftSender
import com.otus.mail.feature.inbox.domain.KnownPeople
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.domain.MessageBodies
import com.otus.mail.feature.inbox.domain.MessageCleaner
import com.otus.mail.feature.inbox.domain.MessageHeaders
import com.otus.mail.feature.inbox.domain.MessageMarker
import com.otus.mail.feature.inbox.domain.MessageMover
import com.otus.mail.feature.inbox.domain.Newsletters
import com.otus.mail.feature.inbox.domain.PeopleSearch
import com.otus.mail.feature.inbox.domain.ScheduledSends
import com.otus.mail.feature.inbox.domain.SenderBlocks
import com.otus.mail.feature.inbox.domain.SenderHistory
import com.otus.mail.feature.inbox.domain.SignatureRepository
import com.otus.mail.feature.inbox.domain.ThreadHistory
import com.otus.mail.feature.inbox.domain.forgetAccount
import com.otus.mail.feature.inbox.presentation.NewsletterSteps
import com.otus.mail.feature.notifications.data.JmapNewMailChecker
import com.otus.mail.feature.notifications.domain.InstantPush
import com.otus.mail.feature.notifications.domain.MailStateStore
import com.otus.mail.feature.notifications.domain.NewMailChecker
import com.otus.push.PushStateStore
import com.otus.push.PushSubscriptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

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
) {
    internal val nightly: NightlyUpdates get() = host.nightly

    /** The screen and keyboard protections this platform can apply. */
    internal val devicePrivacy: DevicePrivacy get() = host.devicePrivacy

    /** The device's copy of each login's mail; see docs/storage.md. */
    private val accountCache = AccountMailCache(RecoveringMailCache(stores.cache))
    internal val cache: MailCache get() = accountCache
    private val client by lazy(createClient)
    private val connections by lazy { JmapConnections(client, logins) }
    private val avatarImages by lazy { HttpAvatarImageLoader() }
    internal val avatars by lazy {
        AvatarService(
            JmapContactAvatarSource(connections, logins, avatarImages),
            avatarImages,
            domains = PublicSuffixDomainResolver(::avatarSuffixRules),
            iconFallback =
                DomainIconSource { domain ->
                    avatarImages.load("https://icons.duckduckgo.com/ip3/$domain.ico")
                },
            cacheConfig = AvatarCacheConfig(store = avatarStore),
        )
    }

    /** The avatar of each of the user's own addresses, read with [options] like any sender's. */
    internal fun ownAvatars(options: () -> AvatarOptions): JmapOwnAvatars =
        JmapOwnAvatars(connections, logins, avatars, options)

    internal val accounts: AccountRepository by lazy {
        val repository =
            JmapAccountRepository(
                client,
                connections,
                logins,
                AccountService.Mail,
                onAccountChanged = { loginId ->
                    val connected = withContext(Dispatchers.IO) { logins.logins().any { it.id == loginId } }
                    if (connected) accountCache.connected(loginId)
                    avatars.removeAccount(loginId)
                },
                beforeRemove = { loginId ->
                    instantPush?.forget(loginId)
                    stores.folderColors?.forgetAccount(loginId)
                    stores.addressPins?.forgetAccount(loginId)
                    stores.folderNudges?.forgetAccount(loginId)
                    stores.contacts?.forgetAccount(loginId)
                    try {
                        cache.forget(loginId)
                    } catch (error: CacheStorageException) {
                        throw AccountStorageException(error)
                    }
                },
            )
        CacheAccountRepository(repository, logins, accountCache)
    }
    internal val mail: MailSource by lazy { JmapMailSource(connections, logins, cache) }
    internal val mover: MessageMover by lazy { JmapMessageMover(connections, logins, cache) }
    internal val bodies: MessageBodies by lazy { JmapMessageBodies(connections, logins, cache) }
    internal val marker: MessageMarker by lazy { JmapMessageMarker(connections, logins, cache) }
    internal val cleaner: MessageCleaner by lazy { JmapMessageCleaner(connections, logins) }
    internal val drafts: DraftRepository by lazy { JmapDraftRepository(connections, logins) }
    private val jmapSender by lazy { JmapDraftSender(connections, logins) }
    internal val sender: DraftSender get() = jmapSender
    internal val scheduled: ScheduledSends get() = jmapSender
    internal val signatures: SignatureRepository by lazy { JmapSignatures(connections, logins) }
    internal val threads: ThreadHistory by lazy { JmapThreadHistory(connections, logins) }
    internal val uploads: AttachmentUploads by lazy { JmapAttachmentUploads(connections, logins) }
    internal val addressBook: AddressBook by lazy { JmapAddressBook(connections, logins) }
    internal val contacts: ContactRepository by lazy { JmapContactRepository(connections, logins) }

    /** Each login's recent mail, for the people the user deals with most. */
    internal val mailTraces: MailTraceSource by lazy { JmapMailTraces(connections, logins) }
    internal val people: PeopleSearch by lazy { JmapPeopleSearch(connections, logins, addressBook) }
    internal val senderHistory: SenderHistory by lazy { JmapSenderHistory(connections, logins) }
    internal val folders: FolderRepository by lazy { JmapFolderRepository(connections, logins, cache) }
    internal val conversationMessages: ConversationMessages by lazy {
        JmapConversationMessages(connections, logins, cache)
    }
    internal val downloads: AttachmentDownloads by lazy { JmapAttachmentDownloads(connections, logins) }
    internal val knownPeople: KnownPeople by lazy { JmapKnownPeople(connections, logins) }
    internal val newsletters: Newsletters by lazy {
        JmapNewsletters(connections, logins, drafts, sender, cache)
    }
    internal val headers: MessageHeaders by lazy { JmapMessageHeaders(connections, logins) }
    internal val mute: ConversationMute by lazy { JmapConversationMute(connections, logins, cache) }
    internal val blocks: SenderBlocks by lazy { RuleSenderBlocks(rules, folders) }

    /**
     * Toasts and the work their closing starts, such as an unsubscribe sent once its Undo window
     * ends. They belong to the process, so a toast shown before the activity was recreated, as on
     * rotation, still shows and still finishes its work.
     */
    internal val toasts = ToastState()
    internal val work: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    /** Each newsletter strip's step while the process runs, so a reopened message shows it. */
    internal val newsletterSteps = NewsletterSteps()

    /** Lives as long as the process, so its lock also covers the loads of a recreated activity. */
    internal val readLater by lazy { ReadLaterFolders(folders, stores.readLater) }
    internal val rules: FolderRuleRepository by lazy { JmapFolderRuleRepository(connections, logins) }

    /** Finds mail that arrived since the check that last wrote [states], for notifications. */
    fun newMail(states: MailStateStore): NewMailChecker = JmapNewMailChecker(connections, logins, states)

    /**
     * Keeps each login's push subscription pointed at its push endpoint. Create one per process.
     * Servers push only new deliveries (RFC 8621, section 1.5), not flag or folder changes.
     */
    fun pushSubscriptions(
        states: PushStateStore,
        deviceClientId: String,
        beforeCreate: suspend (String) -> Unit = {},
    ): PushSubscriptions =
        PushSubscriptions(
            connections,
            logins,
            states,
            deviceClientId,
            listOf("EmailDelivery"),
            beforeCreate = beforeCreate,
        )

    /**
     * Drops cached bodies of mail received more than [BODY_RETENTION] before [now], so the cache
     * does not grow without bound. Call it once at start; it runs off the main thread.
     */
    suspend fun trimCache(now: Instant = Clock.System.now()) {
        cache.trim(CacheKind.Body, (now - BODY_RETENTION).toEpochMilliseconds())
    }

    /** The server signing key used to register directly with Google (RFC 9749). */
    suspend fun pushVapidKey(login: JmapLogin): String? = connections.connection(login).session().vapidPublicKey

    /** Push cleanup must distinguish an empty account list from locked or unavailable storage. */
    internal fun notificationLogins(): List<JmapLogin> = logins.logins()

    /** The saved logins; none when storage cannot be read. Call it off the main thread. */
    fun logins(): List<JmapLogin> =
        try {
            logins.logins()
        } catch (_: LoginStoreException) {
            emptyList()
        }
}

/** How long a cached body stays after its mail arrived. Opening older mail fetches it again. */
private val BODY_RETENTION = 90.days

@OptIn(ExperimentalResourceApi::class)
private suspend fun avatarSuffixRules(): String = Res.readBytes("files/public_suffix_list.dat").decodeToString()
