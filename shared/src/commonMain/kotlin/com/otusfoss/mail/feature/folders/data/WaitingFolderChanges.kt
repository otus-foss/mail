package com.otusfoss.mail.feature.folders.data

import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.JmapErrorCategory
import com.otusfoss.core.jmap.JmapException
import com.otusfoss.core.jmap.SetMethod
import com.otusfoss.core.jmap.category
import com.otusfoss.core.jmap.isTemporary
import com.otusfoss.core.jmap.login.LoginLifecycle
import com.otusfoss.core.jmap.login.LoginStoreException
import com.otusfoss.mail.core.mail.MailAccount
import com.otusfoss.mail.core.mail.MailboxType
import com.otusfoss.mail.core.mail.mailAccount
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.core.messages.data.LoginLocks
import com.otusfoss.mail.core.storage.DeviceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Renames, moves, and new orders of a login's own folders that wait for the server, kept in
 * [store] as the `Mailbox/set` updates they would have sent (RFC 8621, section 2.5), in the order
 * they were made. A change shows in the folders [mailboxes] keeps at once. Sending drops each one
 * the server answered, refused or not, and has the next load read every mailbox again, so the
 * device shows what the server kept. Only a server that cannot be reached, or a sign-in the user
 * must fix, keeps a change waiting, so one bad answer never blocks the folders. [wake] asks the
 * platform to send them once the network is back. [store] is read and written on [io]; without a
 * store, changes last as long as the process.
 */
class WaitingFolderChanges(
    private val connections: JmapConnections,
    private val mailboxes: Mailboxes,
    private val store: DeviceStore<String, List<JsonObject>>? = null,
    private val wake: (Instant) -> Unit = {},
    private val io: CoroutineContext = Dispatchers.IO,
) : LoginLifecycle {
    // Each login's sends take turns, so a waiting change is never sent twice.
    private val turns = LoginLocks()

    // Every login's changes share one store value, so its read and rewrite happen one at a time.
    private val storeLock = Mutex()
    private var memory = emptyMap<String, List<JsonObject>>()

    // How often each login's first waiting change failed without a clear answer in this process.
    private var tries = emptyMap<String, Int>()

    /** Keeps [update], patches by mailbox id, for [loginId]'s server and shows it in the kept folders. */
    internal suspend fun keep(
        loginId: String,
        update: Map<String, JsonObject>,
    ) {
        change(loginId) { it + JsonObject(update) }
        mailboxes.patchKept(loginId, update)
        wake(Clock.System.now())
    }

    /**
     * Sends [account]'s waiting changes in order. A server that cannot be reached, or a sign-in to
     * fix, leaves the rest waiting and throws [JmapException].
     */
    internal suspend fun send(account: MailAccount) {
        val loginId = account.login.id
        turns.of(loginId).withLock {
            val waiting = saved()[loginId].orEmpty()
            var sent = 0
            try {
                waiting.forEach { update ->
                    sendOne(account, update)
                    sent++
                }
            } finally {
                // A newer load cancels this one; what the server took must still be recorded.
                if (sent > 0) {
                    withContext(NonCancellable) {
                        change(loginId) { it.drop(sent) }
                        mailboxes.rereadNext(loginId)
                    }
                }
            }
        }
    }

    /**
     * Sends one waiting change. It keeps waiting, and this throws, while the server cannot take it
     * now, and for [MAX_TRIES] failures that say nothing clear; a refusal counts as answered.
     */
    private suspend fun sendOne(
        account: MailAccount,
        update: JsonObject,
    ) {
        val loginId = account.login.id
        try {
            account.sendUpdate(update)
        } catch (failure: JmapException) {
            val tried =
                storeLock.withLock {
                    val count = (tries[loginId] ?: 0) + 1
                    tries = tries + (loginId to count)
                    count
                }
            if (failure.waits || tried < MAX_TRIES) throw failure
        }
        storeLock.withLock { tries = tries - loginId }
    }

    /**
     * Sends every login's waiting changes. True when the server could not be reached, so what is
     * left waits for the network; a sign-in to fix waits for the user instead.
     */
    suspend fun flush(): Boolean =
        saved()
            .keys
            .map { loginId ->
                try {
                    connections.mailAccount(loginId)?.let { send(it) }
                    false
                } catch (failure: JmapException) {
                    failure.category == JmapErrorCategory.Offline ||
                        (failure as? JmapException.MethodError)?.isTemporary == true
                } catch (expected: LoginStoreException) {
                    // The saved logins cannot be read now; they go with the next load.
                    false
                }
            }.any { it }

    /** Drops [loginId]'s waiting changes, as the account is removed. */
    override suspend fun removing(loginId: String) {
        turns.of(loginId).withLock { change(loginId) { emptyList() } }
    }

    // A change queued while the login was being removed must not wait for it to come back.
    override suspend fun removed(loginId: String): Unit = removing(loginId)

    private suspend fun saved(): Map<String, List<JsonObject>> = store?.let { withContext(io) { it.read() } } ?: memory

    /** Replaces [loginId]'s waiting changes with what [edit] makes of them. */
    private suspend fun change(
        loginId: String,
        edit: (List<JsonObject>) -> List<JsonObject>,
    ) {
        storeLock.withLock {
            val saved = saved()
            val changes = edit(saved[loginId].orEmpty())
            val all = saved - loginId + if (changes.isEmpty()) emptyMap() else mapOf(loginId to changes)
            memory = all
            store?.let { saving -> withContext(io) { saving.write(all) } }
        }
    }
}

/**
 * Sends one waiting `Mailbox/set` update. A refusal, of the call or of a mailbox, counts as
 * answered, since it would recur. Any other failure throws: one the server says to wait out, or
 * one that says nothing clear, such as HTTP 500 or `serverFail`.
 */
private suspend fun MailAccount.sendUpdate(update: JsonObject) {
    try {
        connection.call(SetMethod(MailboxType, id, update = update.mapValues { (_, patch) -> patch.jsonObject }))
    } catch (failure: JmapException) {
        if (failure.waits || failure.unclear) throw failure
    }
}

/**
 * A failure to wait out: the server is away or busy, a gateway or captive portal is in the way, or
 * the user must sign in.
 */
private val JmapException.waits: Boolean
    get() =
        when (this) {
            is JmapException.MethodError -> type == SERVER_UNAVAILABLE
            is JmapException.RequestFailed -> status in TRY_LATER_STATUSES || limit == MAX_CONCURRENT_REQUESTS
            is JmapException.NotJmap -> true
            else -> category in WAITING_CATEGORIES
        }

/** A failure that says nothing clear about the change, which it survives for a few tries. */
private val JmapException.unclear: Boolean
    get() =
        when (this) {
            is JmapException.MethodError -> isTemporary
            is JmapException.RequestFailed -> status >= SERVER_ERROR
            is JmapException.ResponseTooLarge -> true
            else -> false
        }

private const val MAX_TRIES = 3
private const val SERVER_ERROR = 500
private const val SERVER_UNAVAILABLE = "serverUnavailable"
private const val MAX_CONCURRENT_REQUESTS = "maxConcurrentRequests"

private const val REQUEST_TIMEOUT = 408
private const val TOO_MANY_REQUESTS = 429
private const val BAD_GATEWAY = 502
private const val GATEWAY_TIMEOUT = 504

private val TRY_LATER_STATUSES = setOf(REQUEST_TIMEOUT, TOO_MANY_REQUESTS) + (BAD_GATEWAY..GATEWAY_TIMEOUT)

private val WAITING_CATEGORIES = setOf(JmapErrorCategory.Offline, JmapErrorCategory.Auth, JmapErrorCategory.Credentials)
