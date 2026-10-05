package com.otus.mail.core.avatar

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    private val cache = linkedMapOf<Key, Entry>()

    // Each key shares a lookup; unrelated keys only wait when all fetch permits are in use.
    private val inFlight = mutableMapOf<Key, Lookup>()
    private val fetches = Semaphore(MAX_CONCURRENT_FETCHES)
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
        val entry = lookup(key, normalized)
        return ResolvedAvatar(fallback, entry.image, entry.origin)
    }

    /** Clear after account changes, logout, or explicit contact refresh. In-flight results stay uncached. */
    suspend fun clear() {
        cacheLock.withLock {
            generation++
            cache.clear()
            inFlight.clear()
        }
    }

    private suspend fun lookup(
        key: Key,
        request: AvatarRequest,
    ): Entry {
        var entry: Entry? = null
        while (entry == null) {
            var owner = false
            val pending =
                cacheLock.withLock {
                    freshEntry(key)?.let { return it }
                    inFlight.getOrPut(key) {
                        owner = true
                        Lookup(generation)
                    }
                }
            // A null result means the owner was cancelled; the next pass starts a fresh lookup.
            entry = if (owner) fetchShared(key, request, pending) else pending.result.await()
        }
        return entry
    }

    /** Call with [cacheLock] held. Re-inserting marks the entry as recently used. */
    private fun freshEntry(key: Key): Entry? =
        cache.remove(key)?.takeIf { nowMillis() - it.created < cacheMillis }?.also { cache[key] = it }

    private suspend fun fetchShared(
        key: Key,
        request: AvatarRequest,
        pending: Lookup,
    ): Entry {
        var entry: Entry? = null
        try {
            return fetches.withPermit { fetch(key, request, pending.generation) }.also { entry = it }
        } finally {
            withContext(NonCancellable) {
                cacheLock.withLock { if (inFlight[key] === pending) inFlight.remove(key) }
            }
            pending.result.complete(entry)
        }
    }

    private suspend fun fetch(
        key: Key,
        request: AvatarRequest,
        started: Long,
    ): Entry {
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
                withTimeoutOrNull(DOMAIN_ICON_TIMEOUT_MILLIS) {
                    listOf(host, domain).distinct().firstNotNullOfOrNull { website ->
                        DOMAIN_ICON_PATHS.firstNotNullOfOrNull { path ->
                            avatarOrNull { images.load("https://$website/$path") }
                        }
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

    private class Lookup(
        val generation: Long,
        val result: CompletableDeferred<Entry?> = CompletableDeferred(),
    )

    private companion object {
        const val DEFAULT_CACHE_ENTRIES = 64
        const val DEFAULT_CACHE_MILLIS = 5 * 60 * 1000L
        const val MAX_CONCURRENT_FETCHES = 8

        // Unreachable sites would otherwise cost one loader timeout per host and path.
        const val DOMAIN_ICON_TIMEOUT_MILLIS = 8_000L
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
