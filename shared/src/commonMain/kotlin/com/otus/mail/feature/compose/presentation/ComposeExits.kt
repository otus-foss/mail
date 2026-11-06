package com.otus.mail.feature.compose.presentation

import com.otus.mail.core.messages.presentation.delete
import kotlinx.datetime.LocalDateTime

/** Where the compose page goes when it closes, and what it hands over on the way. */
interface ComposeExits {
    /** Leaves the page; the draft saves what is new. */
    fun close()

    /** Leaves the page after a discard; [delete] removes the saved copy once Undo has passed. */
    fun discarded(delete: () -> Unit)

    /** Leaves the page with the draft to send: now, after an Undo window, or [at] a local time. */
    fun send(
        draft: OutgoingDraft,
        at: LocalDateTime?,
    )
}
