package com.otusfoss.mail.core.messages.data

import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.WaitingDraft
import com.otusfoss.mail.core.messages.domain.isDeviceDraftId
import com.otusfoss.mail.core.storage.DeviceStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant

/**
 * The drafts that wait for the server, in [store], and what became of those it took. The store is
 * read once per process and every change goes through [change], so lists read the drafts without
 * waiting for a save to reach the server. [store] is read and written on [io]; without a store,
 * the drafts last as long as the process.
 */
internal class WaitingDraftShelf(
    private val store: DeviceStore<String, WaitingDraft>?,
    private val io: CoroutineContext,
    private val now: () -> Instant,
    private val wake: (Instant) -> Unit,
) {
    private val lock = Mutex()
    private var drafts: Map<String, WaitingDraft>? = null

    // The Email each draft became once the server took it, by the device id it waited under, so a
    // page that still holds that id replaces the server's copy instead of adding another.
    private var arrived = emptyMap<String, String>()

    suspend fun all(): Map<String, WaitingDraft> = lock.withLock { held() }

    /**
     * Replaces the waiting drafts with what [edit] makes of them, and saves that. A caller that is
     * cancelled meanwhile still finishes it, so memory and the store never disagree.
     */
    suspend fun change(edit: (Map<String, WaitingDraft>) -> Map<String, WaitingDraft>) {
        withContext(NonCancellable) {
            lock.withLock {
                val edited = edit(held())
                drafts = edited
                store?.let { saving -> withContext(io) { saving.write(edited) } }
            }
        }
    }

    /** Keeps [draft] under [id], and asks the platform to store it once the network is back. */
    suspend fun keep(
        id: String,
        draft: Draft,
        discarded: Boolean = false,
    ): String {
        change { it + (id to WaitingDraft(id, draft, now().toEpochMilliseconds(), discarded)) }
        wake(now())
        return id
    }

    /** The draft that waited under [id] no longer waits; the server holds it as [emailId], if any. */
    suspend fun arrive(
        id: String,
        emailId: String?,
    ) {
        withContext(NonCancellable) {
            if (emailId != null) lock.withLock { arrived = arrived + (id to emailId) }
            change { it - id }
        }
    }

    /**
     * The server's copy a save, discard, or send of [emailId] replaces: the one its [waiting] draft
     * names, the Email a draft that waited under it became in this process, or [emailId] itself.
     */
    suspend fun serverCopy(
        emailId: String?,
        waiting: WaitingDraft?,
    ): String? =
        when {
            waiting != null -> waiting.draft.emailId
            emailId != null && isDeviceDraftId(emailId) -> lock.withLock { arrived[emailId] }
            else -> emailId
        }

    private suspend fun held(): Map<String, WaitingDraft> =
        drafts ?: (store?.let { withContext(io) { it.read() } } ?: emptyMap()).also { drafts = it }
}
