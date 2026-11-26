package com.otusfoss.mail.core.messages.data

import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.HandedDrafts
import com.otusfoss.mail.core.messages.domain.isDeviceDraftId
import kotlinx.coroutines.sync.withLock

/** The waiting drafts on [shelf] as the outbox takes them over, in [turns] with saves of the same login. */
internal class OutboxDrafts(
    private val shelf: WaitingDraftShelf,
    private val turns: LoginLocks,
) : HandedDrafts {
    override suspend fun forSending(draft: Draft): Draft =
        turns.of(draft.accountId).withLock {
            draft.copy(emailId = shelf.serverCopy(draft.emailId, draft.emailId?.let { shelf.all()[it] }))
        }

    /** Its device id still names the copy it replaces, so a page reopened by Undo replaces it instead of adding one. */
    override suspend fun handedOver(draft: Draft) {
        turns.of(draft.accountId).withLock {
            draft.emailId?.let { shelf.all()[it] }?.let { shelf.arrive(it.id, it.draft.emailId) }
        }
    }

    override suspend fun takenBack(draft: Draft) {
        val id = draft.emailId?.takeIf(::isDeviceDraftId) ?: return
        turns
            .of(
                draft.accountId,
            ).withLock { shelf.keep(id, draft.copy(emailId = shelf.serverCopy(id, waiting = null))) }
    }
}
