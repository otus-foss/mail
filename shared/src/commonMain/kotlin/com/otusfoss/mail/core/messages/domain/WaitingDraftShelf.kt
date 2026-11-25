package com.otusfoss.mail.core.messages.domain

import com.otusfoss.mail.core.storage.DeviceStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant

/**
 * The drafts that wait for the server, in [store], and what became of those it took. Callers hold
 * [lock] across each read and the change that follows it, so a flush and the page that edits the
 * same draft never store it twice. [store] is read and written on [io]; without a store, the
 * drafts last as long as the process.
 */
internal class WaitingDraftShelf(
    private val store: DeviceStore<String, WaitingDraft>?,
    private val io: CoroutineContext,
    private val now: () -> Instant,
    private val wake: (Instant) -> Unit,
) {
    val lock = Mutex()
    private var memory = emptyMap<String, WaitingDraft>()

    // The Email each draft became once the server took it, by the device id it waited under, so a
    // page that still holds that id replaces the server's copy instead of adding another.
    private var arrived = emptyMap<String, String>()

    suspend fun all(): Map<String, WaitingDraft> = store?.let { withContext(io) { it.read() } } ?: memory

    suspend fun write(drafts: Map<String, WaitingDraft>) {
        memory = drafts
        store?.let { saving -> withContext(io) { saving.write(drafts) } }
    }

    /** Keeps [draft] under [id], and asks the platform to store it once the network is back. */
    suspend fun keep(
        id: String,
        draft: Draft,
    ): String {
        write(all() + (id to WaitingDraft(id, draft, now().toEpochMilliseconds())))
        wake(now())
        return id
    }

    /** The server took the draft that waited under [id] as [emailId]. */
    suspend fun arrive(
        id: String,
        emailId: String,
    ) {
        arrived = arrived + (id to emailId)
        write(all() - id)
    }

    /**
     * The server's copy a save, discard, or send of [emailId] replaces: the one its [waiting] draft
     * names, the Email a draft that waited under it became in this process, or [emailId] itself.
     */
    fun serverCopy(
        emailId: String?,
        waiting: WaitingDraft?,
    ): String? =
        when {
            waiting != null -> waiting.draft.emailId
            emailId != null && isDeviceDraftId(emailId) -> arrived[emailId]
            else -> emailId
        }
}
