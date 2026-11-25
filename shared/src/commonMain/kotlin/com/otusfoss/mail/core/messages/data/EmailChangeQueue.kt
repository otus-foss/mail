package com.otusfoss.mail.core.messages.data

import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.JmapException
import com.otusfoss.core.jmap.login.LoginLifecycle
import com.otusfoss.core.jmap.login.LoginStoreException
import com.otusfoss.mail.core.cache.CacheKind
import com.otusfoss.mail.core.cache.CacheStorageException
import com.otusfoss.mail.core.cache.MailCache
import com.otusfoss.mail.core.cache.withAccount
import com.otusfoss.mail.core.mail.MailAccount
import com.otusfoss.mail.core.mail.QueueFailure
import com.otusfoss.mail.core.mail.QueueTries
import com.otusfoss.mail.core.mail.mailAccount
import com.otusfoss.mail.core.mail.queueFailure
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
    private val tries = mutableMapOf<String, QueueTries>()

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
            tries[loginId]?.reset(patches.keys)
            write(loginId, pending(loginId).merged(patches))
            val refused = send(loginId).refused
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

    /** Sends every login's waiting changes. True when some still wait for the network or server. */
    suspend fun flushAll(): Boolean =
        saved().keys.fold(false) { retry, loginId ->
            locks.of(loginId).withLock { send(loginId).retry } || retry
        }

    private suspend fun pending(loginId: String): Map<String, JsonObject> = saved()[loginId].orEmpty()

    /** Drops [loginId]'s changes, as the account is removed. */
    override suspend fun removing(loginId: String) {
        locks.of(loginId).withLock {
            tries.remove(loginId)
            write(loginId, emptyMap())
        }
    }

    // A change queued while the login was being removed must not wait for it to come back.
    override suspend fun removed(loginId: String): Unit = removing(loginId)

    /**
     * Sends waiting changes batch by batch and drops each one the server answered. A batch that
     * fails as a whole is tried one change at a time so one failing email does not block the rest.
     * Refused emails and emails that fail unclearly too many times leave the queue and the cache.
     */
    private suspend fun send(loginId: String): SendResult {
        val waiting = pending(loginId)
        if (waiting.isEmpty()) return SendResult(emptySet(), retry = false)
        return when (val target = connections.accountFor(loginId)) {
            is QueueAccount.Ready -> {
                val loginTries = tries.getOrPut(loginId) { QueueTries() }
                val run = sendBatches(target.account, waiting, loginTries)
                if (run.left.isEmpty()) tries.remove(loginId) else loginTries.keep(run.left.keys)
                write(loginId, run.left)
                val shouldRetry = run.left.isNotEmpty() && run.retry
                if (shouldRetry) wake(if (run.limited) now() + RATE_LIMIT_WAIT else now())
                if (run.refused.isNotEmpty()) cache.forgetEmails(loginId, run.refused)
                SendResult(run.refused, retry = shouldRetry)
            }

            QueueAccount.Drop -> {
                tries.remove(loginId)
                write(loginId, emptyMap())
                cache.forgetEmails(loginId, waiting.keys)
                SendResult(waiting.keys, retry = false)
            }

            is QueueAccount.Wait -> {
                if (target.retry) wake(now())
                SendResult(emptySet(), retry = target.retry)
            }
        }
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

private sealed interface QueueAccount {
    class Ready(
        val account: MailAccount,
    ) : QueueAccount

    data object Drop : QueueAccount

    class Wait(
        val retry: Boolean,
    ) : QueueAccount
}

private suspend fun JmapConnections.accountFor(loginId: String): QueueAccount =
    try {
        mailAccount(loginId)?.let(QueueAccount::Ready) ?: QueueAccount.Drop
    } catch (_: JmapException.Unsupported) {
        // A login without mail never takes the changes.
        QueueAccount.Drop
    } catch (error: JmapException) {
        QueueAccount.Wait(error.queueFailure().retries)
    } catch (_: LoginStoreException) {
        QueueAccount.Wait(retry = false)
    }

private class SendResult(
    val refused: Set<String>,
    val retry: Boolean,
)

private val QueueFailure.retries: Boolean
    get() = this == QueueFailure.RetryLater || this == QueueFailure.Unclear

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

private val RATE_LIMIT_WAIT = 1.minutes
