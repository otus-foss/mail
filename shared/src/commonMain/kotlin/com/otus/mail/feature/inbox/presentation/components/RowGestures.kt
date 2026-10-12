package com.otus.mail.feature.inbox.presentation.components

import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.MessageActions

/**
 * What message rows do when touched. [actions] supply their swipes, and a long press toggles a row
 * in or out of [selectedIds]. While any row is selected a tap toggles too; otherwise it runs [onOpen].
 */
internal class RowGestures(
    val actions: MessageActions?,
    val selectedIds: Set<String>,
    val onToggle: (MailMessage) -> Unit,
    val onOpen: (MailMessage) -> Unit,
) {
    fun tap(message: MailMessage) = if (selectedIds.isEmpty()) onOpen(message) else onToggle(message)
}
