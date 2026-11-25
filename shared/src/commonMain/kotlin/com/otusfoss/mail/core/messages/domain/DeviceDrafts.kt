package com.otusfoss.mail.core.messages.domain

import com.otusfoss.core.jmap.login.LoginLifecycle
import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.core.storage.DeviceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Saves drafts through [server], and keeps a draft the server cannot be reached for in [store], so
 * what was written offline survives the app closing. Such a save counts as made: it returns a
 * device id (see [isDeviceDraftId]) that later saves, discards, and sends take like an Email id.
 * [flush] stores the waiting drafts on the server once it answers, and [wake] asks the platform to
 * run it when the network is back. [store] is read and written on [io]. Without a store, waiting
 * drafts last as long as the process.
 */
class DeviceDrafts(
    private val server: DraftRepository,
    store: DeviceStore<String, WaitingDraft>?,
    wake: (Instant) -> Unit = {},
    now: () -> Instant = { Clock.System.now() },
    private val newId: () -> String = ::newDeviceDraftId,
    io: CoroutineContext = Dispatchers.IO,
) : DraftRepository,
    LoginLifecycle {
    private val shelf = WaitingDraftShelf(store, io, now, wake)

    /** The drafts that wait, for the lists that show them and the pages that open them. */
    val list = DeviceDraftList(shelf, server)

    override suspend fun identities(accountId: String): List<SendIdentity> = server.identities(accountId)

    override suspend fun save(draft: Draft): Result<String> =
        shelf.lock.withLock {
            val waiting = draft.emailId?.let { shelf.all()[it] }
            val copy = draft.copy(emailId = shelf.serverCopy(draft.emailId, waiting))
            val result = server.save(copy)
            when {
                result.isSuccess -> result.also { if (waiting != null) shelf.arrive(waiting.id, it.getOrThrow()) }

                result.isUnreachable -> Result.success(shelf.keep(waiting?.id ?: newId(), copy))

                // What was typed since still replaces the waiting copy, so a refusal loses none of it.
                else -> result.also { if (waiting != null) shelf.keep(waiting.id, copy) }
            }
        }

    override suspend fun discard(
        accountId: String,
        emailId: String,
    ): Result<Unit> =
        shelf.lock.withLock {
            val waiting = shelf.all()[emailId]
            if (waiting != null) shelf.write(shelf.all() - emailId)
            shelf.serverCopy(emailId, waiting)?.let { server.discard(accountId, it) } ?: Result.success(Unit)
        }

    /**
     * [draft] as it goes to the sender: with the id of the server's copy it replaces, when it was
     * saved on the device. It no longer waits here, since the outbox keeps it from now on.
     */
    suspend fun handOver(draft: Draft): Draft =
        shelf.lock.withLock {
            val waiting = draft.emailId?.let { shelf.all()[it] }
            if (waiting != null) shelf.write(shelf.all() - waiting.id)
            draft.copy(emailId = shelf.serverCopy(draft.emailId, waiting))
        }

    /**
     * Stores the waiting drafts on the server, those of [loginId] or of every login, the one saved
     * first going first. A draft the server refuses stays on the device for the next try. True when
     * some still wait.
     */
    suspend fun flush(loginId: String? = null): Boolean =
        shelf.lock.withLock {
            val due =
                shelf
                    .all()
                    .values
                    .filter { loginId == null || it.loginId == loginId }
                    .sortedBy { it.savedMillis }
            for (waiting in due) {
                val result = server.save(waiting.draft)
                if (result.isSuccess) shelf.arrive(waiting.id, result.getOrThrow())
                if (result.isUnreachable) break
            }
            shelf.all().isNotEmpty()
        }

    /** Drops [loginId]'s waiting drafts, as the account is removed. */
    override suspend fun removing(loginId: String) {
        shelf.lock.withLock { shelf.write(shelf.all().filterValues { it.loginId != loginId }) }
    }

    // A draft kept while the login was being removed must not wait for it to come back.
    override suspend fun removed(loginId: String): Unit = removing(loginId)
}

/** What [DeviceDrafts] keeps on the device, as the lists that show it and the pages that open it read it. */
class DeviceDraftList internal constructor(
    private val shelf: WaitingDraftShelf,
    private val server: DraftRepository,
) : WaitingDraftList {
    override suspend fun waiting(loginId: String): List<WaitingDraft> =
        shelf.lock.withLock {
            shelf
                .all()
                .values
                .filter { it.loginId == loginId }
                .sortedByDescending { it.savedMillis }
        }

    override suspend fun drop(id: String): Boolean =
        shelf.lock.withLock {
            val waiting = shelf.all()[id]
            if (waiting != null) shelf.write(shelf.all() - id)
            // The copy it replaced would show again, so it goes too when the server can be reached.
            waiting?.draft?.emailId?.let { server.discard(waiting.loginId, it) }
            waiting != null
        }

    /** The draft that waits under [id], which keeps that id; null when none does. */
    suspend fun load(id: String): Draft? =
        shelf.lock.withLock { shelf.all()[id]?.let { it.draft.copy(emailId = it.id) } }

    /** The Email the draft that waited under [id] became in this process; null when it did not. */
    suspend fun arrivedAs(id: String): String? = shelf.lock.withLock { shelf.serverCopy(id, waiting = null) }
}

/** Whether a save failed only because the server could not be reached, so trying again later can work. */
private val Result<*>.isUnreachable: Boolean
    get() = ((exceptionOrNull() as? DraftException)?.failure as? DraftFailure.Mail)?.failure == MailFailure.Unreachable
