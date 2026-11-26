package com.otusfoss.mail.feature.inbox.presentation.components

import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.presentation.MessageActions
import com.otusfoss.mail.core.messages.presentation.actions.ActionSubject
import com.otusfoss.mail.core.messages.presentation.actions.offer

/**
 * What message rows do when touched. [actions] supply their swipes, and a long press toggles a row
 * in or out of [selectedIds]. While any row is selected a tap toggles too; otherwise it runs [onOpen].
 * Dragging after a long press replaces the selection through [onSelect].
 */
internal class RowGestures(
    val actions: MessageActions?,
    val selectedIds: Set<String>,
    val onToggle: (MailMessage) -> Unit,
    val onSelect: (Set<String>) -> Unit,
    val onOpen: (MailMessage) -> Unit,
) {
    fun tap(message: MailMessage) = if (selectedIds.isEmpty()) onOpen(message) else onToggle(message)

    /** Marks [message] read once its code is copied, as the alert's Copy code does; mail already [read] stays. */
    fun codeCopied(
        message: MailMessage,
        read: Boolean,
    ) {
        if (!read) ActionSubject(actions, listOf(message)).offer(MailAction.Read)?.run()
    }
}
