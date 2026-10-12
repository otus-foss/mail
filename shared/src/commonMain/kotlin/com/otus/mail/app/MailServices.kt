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
import com.otus.jmap.login.LoginStore
import com.otus.mail.designsystem.resources.Res
import com.otus.mail.feature.folders.data.JmapFolderRepository
import com.otus.mail.feature.folders.data.JmapInboxMatcher
import com.otus.mail.feature.folders.data.JmapRuleRepository
import com.otus.mail.feature.folders.domain.FolderRepository
import com.otus.mail.feature.folders.domain.InboxMatcher
import com.otus.mail.feature.folders.domain.ReadLaterFolders
import com.otus.mail.feature.folders.domain.ReadLaterStore
import com.otus.mail.feature.folders.domain.RuleRepository
import com.otus.mail.feature.inbox.data.JmapDraftRepository
import com.otus.mail.feature.inbox.data.JmapMailSource
import com.otus.mail.feature.inbox.data.JmapMessageMarker
import com.otus.mail.feature.inbox.data.JmapMessageMover
import com.otus.mail.feature.inbox.data.JmapSenderHistory
import com.otus.mail.feature.inbox.domain.DraftRepository
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.domain.MessageMarker
import com.otus.mail.feature.inbox.domain.MessageMover
import com.otus.mail.feature.inbox.domain.SenderHistory
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Connects data implementations to feature contracts. Create one per process: it owns the HTTP
 * client. Platform entry points supply their protected [LoginStore] and app-private stores.
 */
class MailServices(
    logins: LoginStore = InMemoryLoginStore(),
    avatarStore: AvatarStore = EmptyAvatarStore,
    readLaterStore: ReadLaterStore? = null,
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
        )
    }
    internal val mail: MailSource by lazy { JmapMailSource(connections, logins) }
    internal val mover: MessageMover by lazy { JmapMessageMover(connections, logins) }
    internal val marker: MessageMarker by lazy { JmapMessageMarker(connections, logins) }
    internal val drafts: DraftRepository by lazy { JmapDraftRepository(connections, logins) }
    internal val senderHistory: SenderHistory by lazy { JmapSenderHistory(connections, logins) }
    internal val folders: FolderRepository by lazy { JmapFolderRepository(connections, logins) }

    /** Lives as long as the process, so its lock also covers the loads of a recreated activity. */
    internal val readLater by lazy { ReadLaterFolders(folders, readLaterStore) }
    internal val rules: RuleRepository by lazy { JmapRuleRepository(connections, logins) }
    internal val inboxMatcher: InboxMatcher by lazy { JmapInboxMatcher(connections, logins) }
}

@OptIn(ExperimentalResourceApi::class)
private suspend fun avatarSuffixRules(): String = Res.readBytes("files/public_suffix_list.dat").decodeToString()
