package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.JmapJson
import com.otus.jmap.SetMethod
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.cache.CacheKind
import com.otus.mail.core.cache.CacheStorageException
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.cache.withAccount
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.feature.inbox.domain.EmailChangeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Marks and moves as `Email/set` patches (RFC 8621, section 4.6) that wait for the server, kept in
 * [store] by login and email, so a change made offline or just before the app closed still
 * arrives. A change shows in the cache at once. A later patch of an email merges into the one
 * waiting. The patches only set or remove keys, so sending one twice changes nothing more. [wake]
 * asks the platform to try again once the network is back. Without a store, changes last as long
 * as the process.
 */
class EmailChangeQueue(
    private val store: EmailChangeStore?,
    private val wake: (Instant) -> Unit = {},
    private val now: () -> Instant = { Clock.System.now() },
) {
    // A login's changes go out one batch after another, in the order they were made.
    private val locks = LoginLocks()
    private var memory = emptyMap<String, String>()

    /**
     * Queues [patches] of [loginId]'s emails and sends every change the login has waiting. Returns
     * the emails whose change the server made or that wait for it, which show in [cache] at once;
     * the server refused the others.
     */
    internal suspend fun update(
        connections: JmapConnections,
        logins: LoginStore,
        loginId: String,
        patches: Map<String, JsonObject>,
        cache: MailCache?,
    ): Set<String> =
        locks.of(loginId).withLock {
            write(loginId, pending(loginId).merged(patches))
            val refused = send(connections, logins, loginId, cache)
            val shown = patches.keys - refused
            if (cache != null) cache.show(loginId, patches.filterKeys { it in shown })
            shown
        }

    /** Sends [loginId]'s waiting changes, as before a sync reads the server's mail. */
    internal suspend fun flush(
        connections: JmapConnections,
        logins: LoginStore,
        loginId: String,
        cache: MailCache?,
    ) {
        locks.of(loginId).withLock { send(connections, logins, loginId, cache) }
    }

    /** Sends every login's waiting changes. True when some still wait for the network. */
    suspend fun flushAll(
        connections: JmapConnections,
        logins: LoginStore,
        cache: MailCache?,
    ): Boolean {
        saved().keys.forEach { flush(connections, logins, it, cache) }
        return saved().isNotEmpty()
    }

    /** The changes of [loginId] the server has not confirmed yet, by email id. */
    internal suspend fun pending(loginId: String): Map<String, JsonObject> = saved()[loginId]?.let(::decoded).orEmpty()

    /** Drops [loginId]'s changes, as when the account is removed. */
    suspend fun forget(loginId: String) {
        locks.of(loginId).withLock { write(loginId, emptyMap()) }
    }

    /**
     * Sends the waiting changes batch by batch and drops each one the server answered. A refused
     * email leaves the cache, so the next sync reads it again. Returns the refused emails.
     */
    private suspend fun send(
        connections: JmapConnections,
        logins: LoginStore,
        loginId: String,
        cache: MailCache?,
    ): Set<String> {
        val waiting = pending(loginId)
        if (waiting.isEmpty()) return emptySet()
        var left = waiting
        var refused = emptySet<String>()
        try {
            val account = mailAccount(connections, logins, loginId)
            if (account == null) {
                // The login was removed, and its changes with it.
                left = emptyMap()
            } else {
                waiting.entries.chunked(account.session.setBatch).forEach { batch ->
                    val (answered, notTaken) = account.sendBatch(batch.associate { it.toPair() })
                    left = left - answered
                    refused = refused + notTaken
                }
            }
        } catch (expected: JmapException) {
            // Offline or a server error: what is left goes with the next sync or background run.
        } catch (expected: LoginStoreException) {
            // The login cannot be read now; its changes wait.
        }
        write(loginId, left)
        if (left.isNotEmpty()) wake(now())
        if (cache != null && refused.isNotEmpty()) cache.forgetEmails(loginId, refused)
        return refused
    }

    private suspend fun saved(): Map<String, String> =
        store?.let { withContext(Dispatchers.IO) { it.read() } } ?: memory

    private suspend fun write(
        loginId: String,
        changes: Map<String, JsonObject>,
    ) {
        val all = saved() - loginId + if (changes.isEmpty()) emptyMap() else mapOf(loginId to encoded(changes))
        memory = all
        val saving = store ?: return
        withContext(Dispatchers.IO) { saving.write(all) }
    }
}

/** Sends one batch; returns the emails the server answered for and those it refused. */
private suspend fun MailAccount.sendBatch(batch: Map<String, JsonObject>): Pair<Set<String>, Set<String>> =
    try {
        val result = connection.call(SetMethod(EmailType, id, update = batch))
        result.updated.keys + result.notUpdated.keys to result.notUpdated.keys
    } catch (expected: JmapException.MethodError) {
        // The server refused the whole call, so the batch would fail again.
        batch.keys to batch.keys
    }

/**
 * These patches with [later] ones merged in, email by email. A later key wins; setting a whole
 * property replaces the keys below it, and a key below a property sent whole changes that value,
 * because one patch cannot name a path and a path inside it (RFC 8620, section 5.3).
 */
internal fun Map<String, JsonObject>.merged(later: Map<String, JsonObject>): Map<String, JsonObject> =
    this + later.mapValues { (id, patch) -> get(id)?.mergedPatch(patch) ?: patch }

private fun JsonObject.mergedPatch(later: JsonObject): JsonObject =
    JsonObject(
        later.entries.fold(toMap()) { patch, (path, value) ->
            val property = path.substringBefore('/')
            when {
                path == property -> patch.filterKeys { !it.startsWith("$property/") } + (path to value)
                patch[property] is JsonObject -> JsonObject(patch).patchedAt(path, value)
                else -> patch + (path to value)
            }
        },
    )

/** Shows [patches] in the cached emails; a cache failure does not undo a queued change. */
private suspend fun MailCache.show(
    loginId: String,
    patches: Map<String, JsonObject>,
) {
    withAccount(loginId) { accountCache ->
        try {
            accountCache.patchEmails(loginId, patches)
        } catch (expected: CacheStorageException) {
            // The server has the change or will; the next sync shows it.
        }
    }
}

private suspend fun MailCache.forgetEmails(
    loginId: String,
    ids: Set<String>,
) {
    withAccount(loginId) { accountCache ->
        try {
            accountCache.write(loginId) {
                remove(CacheKind.Email, ids)
                remove(CacheKind.Member, ids)
            }
        } catch (expected: CacheStorageException) {
            // A stale row stays until the next full load.
        }
    }
}

private val CHANGES = MapSerializer(String.serializer(), JsonObject.serializer())

private fun encoded(changes: Map<String, JsonObject>): String = JmapJson.encodeToString(CHANGES, changes)

private fun decoded(text: String): Map<String, JsonObject>? =
    try {
        JmapJson.decodeFromString(CHANGES, text)
    } catch (expected: SerializationException) {
        null
    } catch (expected: IllegalArgumentException) {
        null
    }
