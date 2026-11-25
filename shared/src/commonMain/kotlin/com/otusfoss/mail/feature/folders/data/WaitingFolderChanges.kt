package com.otusfoss.mail.feature.folders.data

import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.JmapException
import com.otusfoss.core.jmap.SetMethod
import com.otusfoss.core.jmap.isTemporary
import com.otusfoss.core.jmap.login.LoginLifecycle
import com.otusfoss.core.jmap.login.LoginStoreException
import com.otusfoss.mail.core.mail.MailAccount
import com.otusfoss.mail.core.mail.MailboxType
import com.otusfoss.mail.core.mail.mailAccount
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.core.storage.DeviceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
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
 * device shows what the server kept. [wake] asks the platform to send them once the network is
 * back. [store] is read and written on [io]; without a store, changes last as long as the process.
 */
class WaitingFolderChanges(
    private val connections: JmapConnections,
    private val mailboxes: Mailboxes,
    private val store: DeviceStore<String, List<JsonObject>>? = null,
    private val wake: (Instant) -> Unit = {},
    private val io: CoroutineContext = Dispatchers.IO,
) : LoginLifecycle {
    // One at a time, so a waiting change is never sent twice and none is lost between a read and a write.
    private val lock = Mutex()
    private var memory = emptyMap<String, List<JsonObject>>()

    /** Keeps [update], patches by mailbox id, for [loginId]'s server and shows it in the kept folders. */
    internal suspend fun keep(
        loginId: String,
        update: Map<String, JsonObject>,
    ) {
        lock.withLock { write(loginId, saved()[loginId].orEmpty() + JsonObject(update)) }
        mailboxes.patchKept(loginId, update)
        wake(Clock.System.now())
    }

    /**
     * Sends [account]'s waiting changes in order. A failed request leaves the rest waiting and
     * throws [JmapException].
     */
    internal suspend fun send(account: MailAccount) {
        val loginId = account.login.id
        lock.withLock {
            val waiting = saved()[loginId].orEmpty()
            var left = waiting
            try {
                while (left.isNotEmpty()) {
                    account.sendUpdate(left.first())
                    left = left.drop(1)
                }
            } finally {
                if (left.size < waiting.size) {
                    write(loginId, left)
                    mailboxes.rereadNext(loginId)
                }
            }
        }
    }

    /** Sends every login's waiting changes. True when some still wait, such as for the network. */
    suspend fun flush(): Boolean {
        saved().keys.forEach { loginId ->
            try {
                connections.mailAccount(loginId)?.let { send(it) }
            } catch (expected: JmapException) {
                // They go with the next load.
            } catch (expected: LoginStoreException) {
                // The saved logins cannot be read now; they go with the next load.
            }
        }
        return saved().isNotEmpty()
    }

    /** Drops [loginId]'s waiting changes, as the account is removed. */
    override suspend fun removing(loginId: String) {
        lock.withLock { write(loginId, emptyList()) }
    }

    // A change queued while the login was being removed must not wait for it to come back.
    override suspend fun removed(loginId: String): Unit = removing(loginId)

    private suspend fun saved(): Map<String, List<JsonObject>> = store?.let { withContext(io) { it.read() } } ?: memory

    private suspend fun write(
        loginId: String,
        changes: List<JsonObject>,
    ) {
        val all = saved() - loginId + if (changes.isEmpty()) emptyMap() else mapOf(loginId to changes)
        memory = all
        store?.let { saving -> withContext(io) { saving.write(all) } }
    }
}

/**
 * Sends one waiting `Mailbox/set` update. A refusal, of the call or of a mailbox, counts as
 * answered, since it would recur; a server that is down for now throws, so the change waits.
 */
private suspend fun MailAccount.sendUpdate(update: JsonObject) {
    try {
        connection.call(SetMethod(MailboxType, id, update = update.mapValues { (_, patch) -> patch.jsonObject }))
    } catch (refusal: JmapException.MethodError) {
        if (refusal.isTemporary) throw refusal
    }
}
