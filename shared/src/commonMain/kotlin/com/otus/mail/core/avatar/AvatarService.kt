package com.otus.mail.core.avatar

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

/** Contact photo, optional domain icon, then local initials. Safe to share within one app process. */
class AvatarService(
    private val contacts: ContactPhotoSource = ContactPhotoSource { _, _ -> null },
    private val images: AvatarImageLoader = AvatarImageLoader { null },
    private val maxEntries: Int = DEFAULT_CACHE_ENTRIES,
    private val cacheMillis: Long = DEFAULT_CACHE_MILLIS,
    private val nowMillis: () -> Long = monotonicMillis(),
    private val domains: AvatarDomainResolver = AvatarDomainResolver { it },
    private val iconFallback: DomainIconSource = DomainIconSource { null },
) {
    private val cacheLock = Mutex()

    // Bounded locks coalesce concurrent lookups of the same key without an unbounded lock map.
    private val lookups = List(LOOKUP_CONCURRENCY) { Mutex() }
    private val cache = linkedMapOf<Key, Entry>()
    private var generation = 0L

    init {
        require(maxEntries > 0 && cacheMillis > 0)
    }

    suspend fun resolve(
        request: AvatarRequest,
        options: AvatarOptions = AvatarOptions(),
    ): ResolvedAvatar {
        val normalized = request.copy(email = avatarEmailKey(request.email))
        val fallback = generatedAvatar(request.name, normalized.email)
        if (!isAvatarEmail(normalized.email)) return ResolvedAvatar(fallback)
        val key = Key(normalized.accountKey, normalized.email, options)
        val entry =
            lookups[(key.hashCode() and Int.MAX_VALUE) % LOOKUP_CONCURRENCY].withLock {
                cached(key) ?: fetch(key, normalized)
            }
        return ResolvedAvatar(fallback, entry.image, entry.origin)
    }

    /** Clear after account changes, logout, or explicit contact refresh. In-flight results stay uncached. */
    suspend fun clear() {
        cacheLock.withLock {
            generation++
            cache.clear()
        }
    }

    private suspend fun cached(key: Key): Entry? =
        cacheLock.withLock {
            cache.remove(key)?.takeIf { nowMillis() - it.created < cacheMillis }?.also { cache[key] = it }
        }

    private suspend fun fetch(
        key: Key,
        request: AvatarRequest,
    ): Entry {
        val started = cacheLock.withLock { generation }
        val contact = avatarOrNull { contacts.photo(request, key.options.externalContactImages) }
        val domain = if (contact == null && key.options.domainIcons) domainImage(request.email, key.options) else null
        val entry =
            Entry(
                contact ?: domain,
                when {
                    contact != null -> AvatarOrigin.Contact
                    domain != null -> AvatarOrigin.Domain
                    else -> null
                },
                nowMillis(),
            )
        cacheLock.withLock {
            if (generation == started) {
                cache[key] = entry
                while (cache.size > maxEntries) cache.remove(cache.keys.first())
            }
        }
        return entry
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
                listOf(host, domain).distinct().firstNotNullOfOrNull { website ->
                    DOMAIN_ICON_PATHS.firstNotNullOfOrNull { path ->
                        avatarOrNull { images.load("https://$website/$path") }
                    }
                }
            direct ?: if (options.domainIconProxy) iconFallback.icon(domain) else null
        }

    private data class Key(
        val account: String,
        val email: String,
        val options: AvatarOptions,
    )

    private class Entry(
        val image: AvatarImage?,
        val origin: AvatarOrigin?,
        val created: Long,
    )

    private companion object {
        const val DEFAULT_CACHE_ENTRIES = 64
        const val DEFAULT_CACHE_MILLIS = 5 * 60 * 1000L
        const val LOOKUP_CONCURRENCY = 4
        val DOMAIN_ICON_PATHS = listOf("apple-touch-icon.png", "favicon.png", "favicon.ico")
    }
}

private fun monotonicMillis(): () -> Long {
    val start = TimeSource.Monotonic.markNow()
    return { start.elapsedNow().inWholeMilliseconds }
}

/** Optional avatar failures never hide mail/events, and cancellation always propagates. */
internal suspend fun avatarOrNull(load: suspend () -> AvatarImage?): AvatarImage? =
    runCatching { load()?.takeIf { it.bytes.isNotEmpty() && it.bytes.size <= MAX_AVATAR_BYTES } }.getOrElse {
        if (it is CancellationException) throw it
        null
    }
