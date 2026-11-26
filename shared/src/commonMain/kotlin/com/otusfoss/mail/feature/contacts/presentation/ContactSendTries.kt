package com.otusfoss.mail.feature.contacts.presentation

import com.otusfoss.mail.core.mail.QUEUE_MAX_TRIES
import com.otusfoss.mail.core.mail.QUEUE_MAX_UNCLEAR
import com.otusfoss.mail.core.mail.QueueTries
import com.otusfoss.mail.feature.contacts.domain.ContactEdit
import com.otusfoss.mail.feature.contacts.domain.ContactWriteFailure

/** What came of sending one change. */
internal enum class SendOutcome { Sent, Refused, Unclear, Offline }

internal val ContactWriteFailure.outcome: SendOutcome
    get() =
        when (this) {
            ContactWriteFailure.Offline -> SendOutcome.Offline
            ContactWriteFailure.Failing -> SendOutcome.Unclear
            else -> SendOutcome.Refused
        }

/** How often each change failed without a clear answer since the app started. */
internal class FailedTries {
    private val tries = QueueTries(MAX_TRIES)

    /**
     * Whether a change [id] that failed with [failure] is set aside now: a refused one at once, one
     * without a clear answer once it failed [MAX_TRIES] times, and one that found no connection never.
     */
    fun setsAside(
        id: String,
        failure: ContactWriteFailure,
    ): Boolean =
        if (failure != ContactWriteFailure.Failing) failure != ContactWriteFailure.Offline else tries.reachedLimit(id)

    /** Forgets the changes that are no longer in [edits]. */
    fun keep(edits: List<ContactEdit>) {
        tries.keep(edits.map { it.id }.toSet())
    }
}

/** How many times a change may fail without a clear answer before it is set aside. */
internal const val MAX_TRIES = QUEUE_MAX_TRIES

/** How many changes in a row may fail without a clear answer before a flush stops. */
internal const val MAX_UNCLEAR = QUEUE_MAX_UNCLEAR
