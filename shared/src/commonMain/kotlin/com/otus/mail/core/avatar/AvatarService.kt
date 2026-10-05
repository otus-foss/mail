package com.otus.mail.core.avatar

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/** Contact photos are account-specific; website icons are shared by every sender at that host. */
class AvatarService(
    private val contacts: ContactPhotoSource = ContactPhotoSource { _, _ -> null },
    private val images: AvatarImageLoader = AvatarImageLoader { null },
    cacheConfig: AvatarCacheConfig = AvatarCacheConfig(),
    private val domains: AvatarDomainResolver = AvatarDomainResolver { it },
    private val iconFallback: DomainIconSource = DomainIconSource { null },
) {
    private val cache =
        AvatarLookupCache(
            cacheConfig.store,
            cacheConfig.maxEntries,
            cacheConfig.cacheMillis,
            cacheConfig.nowMillis,
        )
    private val fetches = Semaphore(MAX_CONCURRENT_FETCHES)

    suspend fun resolve(
        request: AvatarRequest,
        options: AvatarOptions = AvatarOptions(),
    ): ResolvedAvatar {
        val normalized = request.copy(email = avatarEmailKey(request.email))
        val fallback = generatedAvatar(request.name, normalized.email)
        if (!isAvatarEmail(normalized.email)) return ResolvedAvatar(fallback)
        val started = cache.begin()
        val contact =
            cache
                .load(contactKey(normalized, options), started) {
                    fetches.withPermit { avatarOrNull { contacts.photo(normalized, options.externalContactImages) } }
                }.image
        val domain =
            if (contact == null) {
                domainKey(normalized, options)?.let { key ->
                    cache.load(key, started) { fetches.withPermit { domainImage(normalized.email, options) } }.image
                }
            } else {
                null
            }
        val origin =
            when {
                contact != null -> AvatarOrigin.Contact
                domain != null -> AvatarOrigin.Domain
                else -> null
            }
        return ResolvedAvatar(fallback, contact ?: domain, origin)
    }

    /** Local images only, so refreshed rows keep their avatar before network work. */
    suspend fun cached(
        request: AvatarRequest,
        options: AvatarOptions = AvatarOptions(),
    ): ResolvedAvatar? {
        val normalized = request.copy(email = avatarEmailKey(request.email))
        if (!isAvatarEmail(normalized.email)) return null
        val contact = cache.peek(contactKey(normalized, options))
        val fallback = generatedAvatar(request.name, normalized.email)
        val domain = if (contact?.image == null) domainKey(normalized, options)?.let { cache.peek(it) } else null
        return when {
            contact?.image != null -> ResolvedAvatar(fallback, contact.image, AvatarOrigin.Contact)
            domain?.image != null -> ResolvedAvatar(fallback, domain.image, AvatarOrigin.Domain)
            contact != null || domain != null -> ResolvedAvatar(fallback)
            else -> null
        }
    }

    /** Explicit contact refresh invalidates stored results, including queued and running lookups. */
    suspend fun clear() = cache.clear()

    /** Logout/reconnect purges private photos while retaining public website icons. */
    suspend fun removeAccount(account: String) = cache.clear(account)

    private fun contactKey(
        request: AvatarRequest,
        options: AvatarOptions,
    ) = AvatarCacheKey(request.accountKey, "contact:${options.externalContactImages}:${request.email}")

    private fun domainKey(
        request: AvatarRequest,
        options: AvatarOptions,
    ): AvatarCacheKey? =
        if (options.domainIcons) {
            avatarIconDomain(request.email)?.let { AvatarCacheKey(null, "domain:${options.domainIconProxy}:$it") }
        } else {
            null
        }

    private suspend fun domainImage(
        email: String,
        options: AvatarOptions,
    ): AvatarImage? =
        avatarOrNull {
            val host = avatarIconDomain(email) ?: return@avatarOrNull null
            val domain = domains.resolve(host) ?: return@avatarOrNull null
            if (!isPublicAvatarHost(domain) || (host != domain && !host.endsWith(".$domain"))) return@avatarOrNull null
            val direct =
                withTimeoutOrNull(DOMAIN_ICON_TIMEOUT_MILLIS) {
                    listOf(host, domain).distinct().firstNotNullOfOrNull { website ->
                        avatarOrNull { images.websiteIcon(website) }
                            ?: DOMAIN_ICON_PATHS.firstNotNullOfOrNull { path ->
                                avatarOrNull { images.load("https://$website/$path") }
                            }
                    }
                }
            direct ?: if (options.domainIconProxy) iconFallback.icon(domain) else null
        }

    private companion object {
        const val MAX_CONCURRENT_FETCHES = 8
        const val DOMAIN_ICON_TIMEOUT_MILLIS = 8_000L
        val DOMAIN_ICON_PATHS = listOf("apple-touch-icon.png", "favicon.png", "favicon.ico")
    }
}

/** Optional avatar failures never hide mail/events, and cancellation always propagates. */
internal suspend fun avatarOrNull(load: suspend () -> AvatarImage?): AvatarImage? =
    runCatching { load()?.takeIf { it.bytes.isNotEmpty() && it.bytes.size <= MAX_AVATAR_BYTES } }.getOrElse {
        if (it is CancellationException) throw it
        null
    }
