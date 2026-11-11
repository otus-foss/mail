package com.otusfoss.mail.feature.inbox.presentation

import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailScope

/** A change shown before the server saved it. Call [confirm] when the server saves it, or [rollBack]. */
internal class LocalChange(
    private val onConfirm: () -> Unit,
    private val onRollBack: () -> Unit,
) {
    fun confirm() = onConfirm()

    fun rollBack() = onRollBack()
}

/**
 * Local changes to loaded messages, applied again to each loaded snapshot that may predate them.
 * A load can start before a change and finish after it, so a change stays until a load requested
 * after the server confirmed it publishes, and leaves at once when it is rolled back.
 */
internal class PendingChanges {
    private var changes = emptyList<PendingChange>()
    private var nextKey = 0

    /** Records [edit] of the message with [messageId], in [scope] only when it is set. Returns its key. */
    fun add(
        messageId: String,
        scope: MailScope?,
        edit: (MailMessage) -> MailMessage?,
    ): Int {
        val key = nextKey++
        changes = changes + PendingChange(key, messageId, scope, edit)
        return key
    }

    /** Marks the change with [key] saved while [request] was the newest load. */
    fun confirm(
        key: Int,
        request: Int,
    ) {
        changes = changes.map { if (it.key == key) it.copy(confirmedAt = request) else it }
    }

    fun drop(key: Int) {
        changes = changes.filterNot { it.key == key }
    }

    /**
     * [messages] loaded by [request] in [scope], with each change that load may have missed.
     * Changes the server confirmed before [request] started are already in it and are forgotten.
     */
    fun publish(
        messages: List<MailMessage>,
        request: Int,
        scope: MailScope,
    ): List<MailMessage> {
        changes = changes.filter { it.confirmedAt == null || it.confirmedAt >= request }
        return changes
            .filter { it.scope == null || it.scope == scope }
            .fold(messages) { list, change ->
                list.mapNotNull { if (it.id == change.messageId) change.edit(it) else it }
            }
    }
}

private data class PendingChange(
    val key: Int,
    val messageId: String,
    val scope: MailScope?,
    /** The changed message, or null when the change takes it off the list. */
    val edit: (MailMessage) -> MailMessage?,
    val confirmedAt: Int? = null,
)
