package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.MessageActions

/** The open message, the selected rows, and the row gestures that change them. */
internal class InboxRows(
    val opened: MailMessage?,
    val selected: List<MailMessage>,
    val gestures: RowGestures,
    val clearSelection: () -> Unit,
    val close: () -> Unit,
)

/** Which of [messages] is open or selected; system back clears a selection. */
@Composable
internal fun rememberInboxRows(
    messages: List<MailMessage>,
    actions: MessageActions?,
): InboxRows {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    val selected = remember(messages, selectedIds) { messages.filter { it.id in selectedIds } }
    val clearSelection = { selectedIds = emptyList() }
    BackHandler(selected.isNotEmpty(), clearSelection)
    val gestures =
        rowGestures(
            actions,
            selected,
            onToggle = { selectedIds = selectedIds.toggled(it.id) },
            onSelect = { selectedIds = it.toList() },
        ) { openId = it.id }
    return InboxRows(messages.firstOrNull { it.id == openId }, selected, gestures, clearSelection) { openId = null }
}
