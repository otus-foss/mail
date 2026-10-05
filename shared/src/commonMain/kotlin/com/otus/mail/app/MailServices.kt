package com.otus.mail.app

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.LoginStore
import com.otus.mail.core.avatar.AvatarCacheConfig
import com.otus.mail.core.avatar.AvatarService
import com.otus.mail.core.avatar.AvatarStore
import com.otus.mail.core.avatar.DomainIconSource
import com.otus.mail.core.avatar.EmptyAvatarStore
import com.otus.mail.core.avatar.HttpAvatarImageLoader
import com.otus.mail.core.avatar.JmapContactPhotoSource
import com.otus.mail.core.avatar.PublicSuffixDomainResolver
import com.otus.mail.designsystem.resources.Res
import com.otus.mail.feature.accounts.data.JmapAccountRepository
import com.otus.mail.feature.accounts.domain.AccountRepository
import com.otus.mail.feature.inbox.data.JmapMailSource
import com.otus.mail.feature.inbox.domain.MailSource
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Connects data implementations to feature contracts. Create one per process: it owns the HTTP
 * client. Platform entry points supply their protected [LoginStore].
 */
class MailServices(
    logins: LoginStore = InMemoryLoginStore(),
    avatarStore: AvatarStore = EmptyAvatarStore,
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
        JmapAccountRepository(client, connections, logins, onAccountChanged = avatars::removeAccount)
    }
    internal val mail: MailSource by lazy { JmapMailSource(connections, logins) }
}

@OptIn(ExperimentalResourceApi::class)
private suspend fun avatarSuffixRules(): String = Res.readBytes("files/public_suffix_list.dat").decodeToString()
