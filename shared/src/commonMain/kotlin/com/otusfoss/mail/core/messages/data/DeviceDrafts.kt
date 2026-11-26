package com.otusfoss.mail.core.messages.data

import com.otusfoss.core.jmap.login.LoginLifecycle
import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.DraftException
import com.otusfoss.mail.core.messages.domain.DraftFailure
import com.otusfoss.mail.core.messages.domain.DraftRepository
import com.otusfoss.mail.core.messages.domain.HandedDrafts
import com.otusfoss.mail.core.messages.domain.SendIdentity
import com.otusfoss.mail.core.messages.domain.WaitingDraft
import com.otusfoss.mail.core.messages.domain.WaitingDraftList
import com.otusfoss.mail.core.messages.domain.isDeviceDraftId
import com.otusfoss.mail.core.messages.domain.newDeviceDraftId
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
 * device id (see `isDeviceDraftId`) that later saves, discards, and sends take like an Email id.
 * A discard the server cannot be reached for waits the same way. [flush] sends what waits once the
 * server answers, and [wake] asks the platform to run it when the network is back. [store] is read
 * and written on [io]. Without a store, waiting drafts last as long as the process.
 *
 * Each login's saves, discards, and flushes take turns, so a flush and the page that edits the same
 * draft never store it twice. Lists never wait for them.
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
    private val turns = LoginLocks()

    /** The drafts that wait, for the lists that show them and the pages that open them. */
    val list =
        DeviceDraftList(shelf) { loginId, id -> turns.of(loginId).withLock { discard(loginId, id, shelf.all()[id]) } }

    /** Hands waiting drafts to the outbox as they are sent, and takes them back on Undo. */
    val outbox: HandedDrafts = OutboxDrafts(shelf, turns)

    override suspend fun identities(accountId: String): List<SendIdentity> = server.identities(accountId)

    override suspend fun isDraft(
        accountId: String,
        emailId: String,
    ): Result<Boolean> = server.isDraft(accountId, emailId)

    override suspend fun save(draft: Draft): Result<String> =
        turns.of(draft.accountId).withLock {
            val waiting = draft.emailId?.let { shelf.all()[it] }?.takeUnless { it.discarded }
            val copy = draft.copy(emailId = shelf.serverCopy(draft.emailId, waiting))
            val result =
                replacedCopy(
                    draft.accountId,
                    copy.emailId,
                    waiting,
                    draft.emailId,
                ).then { server.save(copy.copy(emailId = it)) }
            when {
                result.isUnreachable -> {
                    Result.success(shelf.keep(waiting?.id ?: newId(), copy.withoutUnuploaded()))
                }

                result.isSuccess -> {
                    if (waiting != null) shelf.arrive(waiting.id, result.getOrThrow())
                    result
                }

                // What was typed since still replaces the waiting copy, so a refusal loses none of it.
                else -> {
                    if (waiting != null) shelf.keep(waiting.id, copy.withoutUnuploaded())
                    result
                }
            }
        }

    override suspend fun discard(
        accountId: String,
        emailId: String,
    ): Result<Unit> = turns.of(accountId).withLock { discard(accountId, emailId, shelf.all()[emailId]) }

    /**
     * Sends what waits for [loginId], or for every login, to the server, the oldest first. A draft
     * the server refuses stays on the device for the next try. True when the server could not be
     * reached, so what is left waits for the network.
     */
    suspend fun flush(loginId: String? = null): Boolean =
        shelf
            .all()
            .values
            .filter { loginId == null || it.loginId == loginId }
            .sortedBy { it.savedMillis }
            .groupBy { it.loginId }
            .map { (login, due) -> turns.of(login).withLock { send(due) } }
            .any { it }

    /** Drops [loginId]'s waiting drafts, as the account is removed. */
    override suspend fun removing(loginId: String) {
        turns.of(loginId).withLock { shelf.change { all -> all.filterValues { it.loginId != loginId } } }
    }

    // A draft kept while the login was being removed must not wait for it to come back.
    override suspend fun removed(loginId: String): Unit = removing(loginId)

    /** Sends [due] in order; true when the server could not be reached. */
    private suspend fun send(due: List<WaitingDraft>): Boolean {
        for (listed in due) {
            // A page may have saved or discarded it while this flush waited for its turn.
            val waiting = shelf.all()[listed.id] ?: continue
            val result =
                replacedCopy(waiting.loginId, waiting.draft.emailId, waiting).then { replaced ->
                    when {
                        !waiting.discarded -> server.save(waiting.draft.copy(emailId = replaced))
                        replaced != null -> server.discard(waiting.loginId, replaced).map { null }
                        else -> Result.success(null)
                    }
                }
            when {
                result.isUnreachable -> {
                    return true
                }

                result.isSuccess || waiting.discarded -> {
                    shelf.arrive(waiting.id, result.getOrNull())
                }
            }
        }
        return false
    }

    /**
     * Drops the waiting draft [id] and destroys the server's copy it names. The draft becomes a
     * discarded entry before the server is asked, so the lists hide that copy and a process that
     * ends meanwhile still destroys it; the entry goes once the server has answered.
     */
    private suspend fun discard(
        loginId: String,
        id: String,
        waiting: WaitingDraft?,
    ): Result<Unit> {
        val replaced = shelf.serverCopy(id, waiting)
        val entry = waiting?.id ?: newId()
        if (replaced != null) shelf.keep(entry, Draft(loginId, emailId = replaced), discarded = true)
        val result =
            replacedCopy(loginId, replaced, waiting, id).then { copy ->
                copy?.let { server.discard(loginId, it) } ?: Result.success(Unit)
            }
        if (!result.isUnreachable) shelf.change { it - entry }
        return if (result.isUnreachable) Result.success(Unit) else result
    }
}

/**
 * [replaced] when the server still holds it as a draft; null when it is gone or another client sent
 * it, which keeps its id (RFC 8621, section 7.5), so it must not be destroyed. Only a copy a
 * [waiting] draft named before time offline is looked up; a page's own recent copy is not.
 */
private suspend fun DeviceDrafts.replacedCopy(
    loginId: String,
    replaced: String?,
    waiting: WaitingDraft?,
    emailId: String? = null,
): Result<String?> =
    // A device id still names the copy its draft replaced after Undo of a send, so it is looked up too.
    if (replaced == null || (waiting == null && emailId?.let(::isDeviceDraftId) != true)) {
        Result.success(replaced)
    } else {
        isDraft(loginId, replaced).map { draft -> replaced.takeIf { draft } }
    }

private inline fun <T, R> Result<T>.then(next: (T) -> Result<R>): Result<R> = fold(next) { Result.failure(it) }

/** What [DeviceDrafts] keeps on the device, as the lists that show it and the pages that open it read it. */
class DeviceDraftList internal constructor(
    private val shelf: WaitingDraftShelf,
    private val discard: suspend (loginId: String, id: String) -> Result<Unit>,
) : WaitingDraftList {
    override suspend fun waiting(loginId: String): List<WaitingDraft> =
        shelf
            .all()
            .values
            .filter { it.loginId == loginId }
            .sortedByDescending { it.savedMillis }

    override suspend fun drop(id: String): Boolean {
        val waiting = shelf.all()[id]?.takeUnless { it.discarded } ?: return false
        discard(waiting.loginId, id)
        return true
    }

    /** The draft that waits under [id], which keeps that id; null when none does. */
    suspend fun load(id: String): Draft? =
        shelf.all()[id]?.takeUnless { it.discarded }?.let { it.draft.copy(emailId = it.id) }

    /** The Email the draft that waited under [id] became in this process; null when it did not. */
    suspend fun arrivedAs(id: String): String? = shelf.serverCopy(id, waiting = null)
}

/** Whether a save failed only because the server could not be reached, so trying again later can work. */
private val Result<*>.isUnreachable: Boolean
    get() = ((exceptionOrNull() as? DraftException)?.failure as? DraftFailure.Mail)?.failure == MailFailure.Unreachable

/** Files attached offline have no upload, so they are not kept in a waiting draft. */
private fun Draft.withoutUnuploaded(): Draft = copy(attachments = attachments.filter { it.uploadedTo(accountId) })
