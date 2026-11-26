package com.otusfoss.mail.core.messages.domain

/**
 * Drafts kept on the device that the outbox takes over as they are sent. Each [Draft] here holds
 * the id it was saved under, which for a draft written offline is its device id.
 */
interface HandedDrafts {
    /** [draft] as the sender needs it: naming the server's copy it replaces, if it has one. */
    suspend fun forSending(draft: Draft): Draft

    /** The outbox keeps [draft] now, so it no longer waits on the device. */
    suspend fun handedOver(draft: Draft)

    /** Undo took [draft] back from the outbox, so it waits on the device again. */
    suspend fun takenBack(draft: Draft)

    /** No drafts wait on the device. */
    object None : HandedDrafts {
        override suspend fun forSending(draft: Draft): Draft = draft

        override suspend fun handedOver(draft: Draft) = Unit

        override suspend fun takenBack(draft: Draft) = Unit
    }
}
