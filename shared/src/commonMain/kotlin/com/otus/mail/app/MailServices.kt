package com.otus.mail.app

import com.otus.accounts.data.JmapAccountRepository
import com.otus.accounts.domain.AccountRepository
import com.otus.accounts.domain.AccountService
import com.otus.avatar.AvatarCacheConfig
import com.otus.avatar.AvatarService
import com.otus.avatar.AvatarStore
import com.otus.avatar.DomainIconSource
import com.otus.avatar.EmptyAvatarStore
import com.otus.avatar.HttpAvatarImageLoader
import com.otus.avatar.JmapContactPhotoSource
import com.otus.avatar.PublicSuffixDomainResolver
import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.designsystem.resources.Res
import com.otus.mail.feature.folders.data.JmapFolderRepository
import com.otus.mail.feature.folders.data.JmapFolderRuleRepository
import com.otus.mail.feature.folders.domain.FolderRepository
import com.otus.mail.feature.folders.domain.FolderRuleRepository
import com.otus.mail.feature.folders.domain.ReadLaterFolders
import com.otus.mail.feature.folders.domain.ReadLaterStore
import com.otus.mail.feature.folders.domain.forgetAccount
import com.otus.mail.feature.inbox.data.JmapAddressBook
import com.otus.mail.feature.inbox.data.JmapAttachmentUploads
import com.otus.mail.feature.inbox.data.JmapDraftRepository
import com.otus.mail.feature.inbox.data.JmapDraftSender
import com.otus.mail.feature.inbox.data.JmapMailSource
import com.otus.mail.feature.inbox.data.JmapMessageBodies
import com.otus.mail.feature.inbox.data.JmapMessageCleaner
import com.otus.mail.feature.inbox.data.JmapMessageMarker
import com.otus.mail.feature.inbox.data.JmapMessageMover
import com.otus.mail.feature.inbox.data.JmapSenderHistory
import com.otus.mail.feature.inbox.data.JmapSignatures
import com.otus.mail.feature.inbox.data.JmapThreadHistory
import com.otus.mail.feature.inbox.domain.AddressBook
import com.otus.mail.feature.inbox.domain.AttachmentUploads
import com.otus.mail.feature.inbox.domain.DraftRepository
import com.otus.mail.feature.inbox.domain.DraftSender
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.domain.MessageBodies
import com.otus.mail.feature.inbox.domain.MessageCleaner
import com.otus.mail.feature.inbox.domain.MessageMarker
import com.otus.mail.feature.inbox.domain.MessageMover
import com.otus.mail.feature.inbox.domain.ScheduledSends
import com.otus.mail.feature.inbox.domain.SenderHistory
import com.otus.mail.feature.inbox.domain.SignatureRepository
import com.otus.mail.feature.inbox.domain.ThreadHistory
import com.otus.mail.feature.inbox.domain.forgetAccount
import com.otus.mail.feature.notifications.data.JmapNewMailChecker
import com.otus.mail.feature.notifications.domain.InstantPush
import com.otus.mail.feature.notifications.domain.MailStateStore
import com.otus.mail.feature.notifications.domain.NewMailChecker
import com.otus.push.PushStateStore
import com.otus.push.PushSubscriptions
import io.ktor.client.HttpClient
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Connects data implementations to feature contracts. Create one per process: it owns the HTTP
 * client. Platform entry points supply their protected [LoginStore] and app-private stores.
 */
class MailServices(
    private val logins: LoginStore = InMemoryLoginStore(),
    avatarStore: AvatarStore = EmptyAvatarStore,
    internal val stores: MailDeviceStores = MailDeviceStores(),
    internal val nightly: NightlyUpdates = NightlyUpdates(),
    /** Instant pushes through the user's UnifiedPush app; null where the platform has none. */
    internal val instantPush: InstantPush? = null,
    internal val pickers: MailPickers = MailPickers(),
    createClient: () -> JmapClient = { JmapClient() },
) {
    private val client by lazy(createClient)
    private val connections by lazy { JmapConnections(client, logins) }
    private val avatarImages by lazy { HttpAvatarImageLoader() }
    internal val avatars by lazy {
        AvatarService(
            JmapContactPhotoSource(connections, logins, avatarImages),
            avatarImages,
            domains = PublicSuffixDomainResolver(::avatarSuffixRules),
            iconFallback =
                DomainIconSource { domain ->
                    avatarImages.load("https://icons.duckduckgo.com/ip3/$domain.ico")
                },
            cacheConfig = AvatarCacheConfig(store = avatarStore),
        )
    }
    internal val accounts: AccountRepository by lazy {
        JmapAccountRepository(
            client,
            connections,
            logins,
            AccountService.Mail,
            onAccountChanged = avatars::removeAccount,
            beforeRemove = { loginId ->
                instantPush?.forget(loginId)
                stores.folderColors?.forgetAccount(loginId)
                stores.addressPins?.forgetAccount(loginId)
                stores.folderNudges?.forgetAccount(loginId)
            },
        )
    }
    internal val mail: MailSource by lazy { JmapMailSource(connections, logins) }
    internal val mover: MessageMover by lazy { JmapMessageMover(connections, logins) }
    internal val bodies: MessageBodies by lazy { JmapMessageBodies(connections, logins) }
    internal val marker: MessageMarker by lazy { JmapMessageMarker(connections, logins) }
    internal val cleaner: MessageCleaner by lazy { JmapMessageCleaner(connections, logins) }
    internal val drafts: DraftRepository by lazy { JmapDraftRepository(connections, logins) }
    private val jmapSender by lazy { JmapDraftSender(connections, logins) }
    internal val sender: DraftSender get() = jmapSender
    internal val scheduled: ScheduledSends get() = jmapSender
    internal val signatures: SignatureRepository by lazy { JmapSignatures(connections, logins) }
    internal val threads: ThreadHistory by lazy { JmapThreadHistory(connections, logins) }
    internal val uploads: AttachmentUploads by lazy { JmapAttachmentUploads(connections, logins) }
    internal val addressBook: AddressBook by lazy { JmapAddressBook(connections, logins) }
    internal val senderHistory: SenderHistory by lazy { JmapSenderHistory(connections, logins) }
    internal val folders: FolderRepository by lazy { JmapFolderRepository(connections, logins) }

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
    ): PushSubscriptions = PushSubscriptions(connections, logins, states, deviceClientId, listOf("EmailDelivery"))

    /** The saved logins; none when storage cannot be read. Call it off the main thread. */
    fun logins(): List<JmapLogin> =
        try {
            logins.logins()
        } catch (_: LoginStoreException) {
            emptyList()
        }
}

@OptIn(ExperimentalResourceApi::class)
private suspend fun avatarSuffixRules(): String = Res.readBytes("files/public_suffix_list.dat").decodeToString()
