package com.otusfoss.mail.core.messages.data

import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.JmapErrorCategory
import com.otusfoss.core.jmap.JmapException
import com.otusfoss.core.jmap.SetMethod
import com.otusfoss.core.jmap.category
import com.otusfoss.core.jmap.isTemporary
import com.otusfoss.core.jmap.login.LoginLifecycle
import com.otusfoss.core.jmap.login.LoginStoreException
import com.otusfoss.mail.core.cache.CacheKind
import com.otusfoss.mail.core.cache.CacheStorageException
import com.otusfoss.mail.core.cache.MailCache
import com.otusfoss.mail.core.cache.withAccount
import com.otusfoss.mail.core.mail.EmailType
import com.otusfoss.mail.core.mail.MailAccount
import com.otusfoss.mail.core.mail.mailAccount
import com.otusfoss.mail.core.storage.DeviceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Marks and moves as `Email/set` patches (RFC 8621, section 4.6) that wait for the server, kept in
 * [store] by login and email, so a change made offline or just before the app closed still
 * arrives. A change shows in [cache] at once. A later patch of an email merges into the one
 * waiting. The patches only set or remove keys, so sending one twice changes nothing more. [wake]
 * asks the platform to try again once the network is back. [store] is read and written on [io].
 * Without a store, changes last as long as the process.
 */
class EmailChangeQueue(
    private val connections: JmapConnections,
    private val cache: MailCache,
    private val store: DeviceStore<String, Map<String, JsonObject>>?,
    private val wake: (Instant) -> Unit = {},
    private val now: () -> Instant = { Clock.System.now() },
    private val io: CoroutineContext = Dispatchers.IO,
) : LoginLifecycle {
    // A login's changes go out one batch after another, in the order they were made.
    private val locks = LoginLocks()

    // Every login's changes share one store value, so its read and rewrite happen one at a time.
    private val storeLock = Mutex()
    private var memory = emptyMap<String, Map<String, JsonObject>>()

    /**
     * Queues [patches] of [loginId]'s emails and sends every change the login has waiting. Returns
     * the emails whose change the server made or that wait for it, which show in the cache at
     * once; the server refused the others.
     */
    internal suspend fun update(
        loginId: String,
        patches: Map<String, JsonObject>,
    ): Set<String> =
        locks.of(loginId).withLock {
            write(loginId, pending(loginId).merged(patches))
            val refused = send(loginId)
            val shown = patches.keys - refused
            cache.show(loginId, patches.filterKeys { it in shown })
            shown
        }

    /**
     * Sends [loginId]'s waiting changes, then runs [sync], which rewrites the login's cached mail
     * from the server, and shows what still waits on top of it. Changes made meanwhile wait for the
     * sync to end, so it never writes back a copy the server had before it confirmed one of them.
     */
    internal suspend fun <T> syncing(
        loginId: String,
        sync: suspend () -> T,
    ): T =
        locks.of(loginId).withLock {
            send(loginId)
            val result = sync()
            cache.show(loginId, pending(loginId))
            result
        }

    /**
     * Sends [loginId]'s waiting changes, then runs [remove] only once none wait, so deleting for
     * good reads the folders as the user left them. Null when some still wait, such as offline or
     * after a server error.
     */
    internal suspend fun <T> afterSending(
        loginId: String,
        remove: suspend () -> T,
    ): T? =
        locks.of(loginId).withLock {
            send(loginId)
            if (pending(loginId).isEmpty()) remove() else null
        }

    /** Sends every login's waiting changes. True when some still wait for the network. */
    suspend fun flushAll(): Boolean {
        saved().keys.forEach { loginId -> locks.of(loginId).withLock { send(loginId) } }
        return saved().isNotEmpty()
    }

    private suspend fun pending(loginId: String): Map<String, JsonObject> = saved()[loginId].orEmpty()

    /** Drops [loginId]'s changes, as the account is removed. */
    override suspend fun removing(loginId: String) {
        locks.of(loginId).withLock { write(loginId, emptyMap()) }
    }

    // A change queued while the login was being removed must not wait for it to come back.
    override suspend fun removed(loginId: String): Unit = removing(loginId)

    /**
     * Sends the waiting changes batch by batch and drops each one the server answered. A refused
     * email leaves the cache, so the next sync reads it again. Returns the refused emails; every
     * one when the login was removed, and its changes with it, or has no mail.
     */
    private suspend fun send(loginId: String): Set<String> {
        val waiting = pending(loginId)
        if (waiting.isEmpty()) return emptySet()
        var left = waiting
        var refused = emptySet<String>()
        var limited = false
        var retry = true
        try {
            val account =
                try {
                    connections.mailAccount(loginId)
                } catch (expected: JmapException.Unsupported) {
                    // A login without mail never takes the changes.
                    null
                }
            if (account == null) {
                left = emptyMap()
                refused = waiting.keys
            } else {
                waiting.entries.chunked(account.session.setBatch).forEach { batch ->
                    val (answered, notTaken) = account.sendBatch(batch.associate { it.toPair() })
                    limited = limited || answered.size < batch.size
                    left = left - answered
                    refused = refused + notTaken
                }
            }
        } catch (error: JmapException) {
            // What is left goes with the next sync or background run. Only being offline or a server
            // error passes by itself; a rejected sign-in or a lost secret waits for the user.
            retry = error.category == JmapErrorCategory.Offline || error.category == JmapErrorCategory.Server
        } catch (expected: LoginStoreException) {
            // The saved logins cannot be read now; the changes wait for the next sync.
            retry = false
        }
        write(loginId, left)
        // A server that asked to slow down gets the next try only after a while.
        if (left.isNotEmpty() && retry) wake(if (limited) now() + RATE_LIMIT_WAIT else now())
        if (refused.isNotEmpty()) cache.forgetEmails(loginId, refused)
        return refused
    }

    private suspend fun saved(): Map<String, Map<String, JsonObject>> =
        store?.let { withContext(io) { it.read() } } ?: memory

    private suspend fun write(
        loginId: String,
        changes: Map<String, JsonObject>,
    ) {
        storeLock.withLock {
            val all = saved() - loginId + if (changes.isEmpty()) emptyMap() else mapOf(loginId to changes)
            memory = all
            store?.let { saving -> withContext(io) { saving.write(all) } }
        }
    }
}

/**
 * Sends one batch; returns the emails the server answered for and those it refused. An email the
 * server would not change for now, such as with `rateLimit` (RFC 8620, section 5.3), is neither,
 * so its change waits for the next try.
 */
private suspend fun MailAccount.sendBatch(batch: Map<String, JsonObject>): Pair<Set<String>, Set<String>> =
    try {
        val result = connection.call(SetMethod(EmailType, id, update = batch))
        val refused = result.notUpdated.filterValues { it.type !in TEMPORARY_SET_ERRORS }.keys
        result.updated.keys + refused to refused
    } catch (error: JmapException.MethodError) {
        // A server down for now may take the batch later; any other refusal of the call would recur.
        if (error.isTemporary) throw error
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

/** `SetError` types that may not recur when the same change is sent again later. */
private val TEMPORARY_SET_ERRORS = setOf("rateLimit")

private val RATE_LIMIT_WAIT = 1.minutes
