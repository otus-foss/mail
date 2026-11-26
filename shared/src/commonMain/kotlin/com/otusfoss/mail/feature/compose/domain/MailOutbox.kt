package com.otusfoss.mail.feature.compose.domain

import com.otusfoss.mail.core.messages.domain.AnsweredMessage
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.HandedDrafts
import com.otusfoss.mail.core.messages.domain.SendCancel
import com.otusfoss.mail.core.messages.domain.SentMessage
import com.otusfoss.mail.core.messages.domain.accountId
import com.otusfoss.mail.core.messages.domain.answered
import com.otusfoss.mail.core.messages.domain.isDeviceDraftId
import com.otusfoss.mail.core.storage.DeviceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Messages handed over for sending, kept in [store] until their send is final on the server, so a
 * closed app or a dropped connection loses none. Each gets its own Message-ID, so after a send whose
 * answer was lost, the [server] can tell whether it went before it goes again. A message the
 * server holds for Undo stays as well, so Undo and sending it early, both through the [server], still
 * happen when the app closes before the server answers. [wake] asks the platform to run [resume]
 * at a time even if the app is closed by then. [store] is read and written on [io]. Without a
 * store, messages last as long as the process.
 * A draft that waited on the device leaves [drafts] only once its entry is saved, and an Undo
 * that stops it before any try gives it back, so it is never in neither.
 */
class MailOutbox(
    store: DeviceStore<String, String>?,
    server: OutboxServer,
    private val wake: (Instant) -> Unit = {},
    private val now: () -> Instant = { Clock.System.now() },
    io: CoroutineContext = Dispatchers.IO,
    private val drafts: HandedDrafts = HandedDrafts.None,
) {
    // One send at a time, so Undo, a retry, and a background run never send the same message twice.
    private val lock = Mutex()
    private val entries = OutboxEntries(store, io)
    private val sends = OutboxSends(entries, server.sender, server.checker, wake, now)
    private val holds = OutboxHolds(entries, server.scheduled, server.checker, wake, now)

    /** Keeps [draft] to go at [due], or at [sendAt] when the user chose a time, and returns its id. */
    suspend fun add(
        draft: Draft,
        answered: AnsweredMessage?,
        due: Instant,
        sendAt: Instant? = null,
    ): String {
        val id = newOutboxId()
        val entry =
            OutboxEntry(
                id,
                drafts.forSending(draft).copy(messageId = "$id@otus.mail"),
                answered,
                due.toEpochMilliseconds(),
                sendAt?.toEpochMilliseconds(),
                deviceDraftId = draft.emailId?.takeIf(::isDeviceDraftId),
            )
        lock.withLock { entries.add(entry) }
        drafts.handedOver(draft)
        // Later than the app itself sends it, so a background run only takes over from a closed app.
        wake(due + BACKGROUND_DELAY)
        return id
    }

    /**
     * Keeps the discard of a draft that edited the scheduled send [held], and returns its id. At
     * [due], once Undo of the discard has passed, [cancel] or [resume] cancels the send and
     * destroys its copy, also when the app is closed by then. [drop] before that keeps the send.
     */
    suspend fun discard(
        held: SentMessage,
        due: Instant,
    ): String {
        val id = newOutboxId()
        val entry =
            OutboxEntry(
                id,
                Draft(held.accountId, emailId = held.emailId),
                dueMillis = due.toEpochMilliseconds(),
                attempted = true,
                withdrawn = true,
                storedCopy = StoredCopy(held.emailId, now().toEpochMilliseconds()),
            )
        lock.withLock { entries.add(entry) }
        wake(due + BACKGROUND_DELAY)
        return id
    }

    /**
     * Takes a message back, as on Undo, and says what became of it. The server is asked at once to
     * cancel a hold it has, or may have when an earlier try got no answer. While it cannot be
     * reached, the message stays as withdrawn and [resume] asks again. A missing message already
     * went, unless a [resume] in this process took it back.
     */
    suspend fun cancel(id: String): SendCancel =
        lock.withLock {
            val entry = entries.find(id) ?: return@withLock holds.takenBack(id) ?: SendCancel.Final
            val outcome = holds.withdraw(entry)
            // A message no try handed over is still the device's draft; one the server held is its draft again.
            if (!entry.attempted) entry.deviceDraftId?.let { drafts.takenBack(entry.draft.copy(emailId = it)) }
            outcome
        }

    /**
     * Forgets a message the user took back to edit, as from a "Couldn't send" toast, or a discard
     * the user undid. False when it was gone already.
     */
    suspend fun drop(id: String): Boolean =
        lock.withLock {
            val kept = entries.find(id) != null
            entries.remove(id)
            kept
        }

    /**
     * Sends message [id] now, or for the server to hold until [holdUntil]. A message an earlier try
     * may have sent is looked for first. The result holds the server's copy, or null when it went
     * before; a missing message counts as sent, and so does one the server holds or the user took
     * back, which this call leaves alone. A message stays after a failure: on a dropped connection
     * to go again by itself, otherwise with a [OutboxProblem] until the user acts. The caller shows
     * that failure, so [offers] leaves the message out for the rest of this process.
     */
    suspend fun send(
        id: String,
        holdUntil: Instant? = null,
    ): Result<SentMessage?> =
        lock.withLock {
            val entry = entries.find(id)?.takeUnless { it.withdrawn || it.held != null }
            if (entry == null) Result.success(null) else entries.shown(id, sends.deliver(entry, holdUntil))
        }

    /**
     * Sends message [id], which the server holds for Undo, at once. The request is kept first, so
     * [resume] tries again until the server answers. A missing message went. A refused one stays
     * with a [OutboxProblem] and its copy, a draft again, to open; as in [send], the caller shows that.
     */
    suspend fun sendNow(id: String): Result<Unit> =
        lock.withLock { entries.find(id)?.let { entries.shown(id, holds.sendNow(it)) } ?: Result.success(Unit) }

    /**
     * Takes the next step of each message that needs no user, as after the app was closed or
     * offline. It sends what is due, asks the server again about a message the user took back or
     * wanted sent at once, and forgets a held message once its hold is over. A failed send that
     * may have gone is looked up, so [offers] names only messages the server does not have.
     */
    suspend fun resume() {
        entries.all().forEach { listed ->
            lock.withLock { entries.find(listed.id)?.let { step(it, it.due <= now(), sends, holds) } }
        }
    }

    /**
     * The messages that need the user and were not offered before in this process, to offer now.
     * That includes one a [resume] could not send, which had nobody to tell.
     */
    suspend fun offers(): List<OutboxEntry> = lock.withLock { entries.unoffered() }

    /** Drops [accountId]'s messages, as when the account is removed. */
    suspend fun forget(accountId: String) {
        lock.withLock { entries.removeIf { it.draft.accountId == accountId } }
    }

    /** Whether a message waits for the server to answer, so a background run should try again. */
    suspend fun waiting(): Boolean = entries.all().any { it.awaitsServer }
}

/** What [entry] needs without the user, [due] once its time has come, done through [sends] and [holds]. */
private suspend fun step(
    entry: OutboxEntry,
    due: Boolean,
    sends: OutboxSends,
    holds: OutboxHolds,
) {
    when {
        entry.mayHaveGone -> sends.confirm(entry)
        entry.problem != null -> Unit
        entry.withdrawn -> if (due) holds.withdraw(entry)
        entry.held != null -> holds.resume(entry)
        due -> sends.deliver(entry, holdUntil = null)
    }
}

private val BACKGROUND_DELAY = 30.seconds

@OptIn(ExperimentalUuidApi::class)
private fun newOutboxId(): String = Uuid.random().toString()
