package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.MessageActions

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
}

/** Row taps, long presses, and drags: opening marks a message read as the saved behavior asks. */
private fun rowGestures(
    actions: MessageActions?,
    selected: List<MailMessage>,
    onToggle: (MailMessage) -> Unit,
    onSelect: (Set<String>) -> Unit,
    onOpen: (MailMessage) -> Unit,
): RowGestures =
    RowGestures(actions, selected.map { it.id }.toSet(), onToggle, onSelect) { message ->
        actions?.opened(message)
        onOpen(message)
    }

private fun List<String>.toggled(id: String): List<String> = if (id in this) this - id else this + id

/** The list's selected messages, a way to clear them, and the row gestures that change them. */
internal class ListSelection(
    val selected: List<MailMessage>,
    val clear: () -> Unit,
    val gestures: RowGestures,
)

/** Selection that survives configuration changes; a tap outside selection runs [onOpen]. */
@Composable
internal fun rememberListSelection(
    messages: List<MailMessage>,
    actions: MessageActions?,
    onOpen: (MailMessage) -> Unit,
): ListSelection {
    var selectedIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    val selected = remember(messages, selectedIds) { messages.filter { it.id in selectedIds } }
    val gestures =
        rowGestures(
            actions,
            selected,
            onToggle = { selectedIds = selectedIds.toggled(it.id) },
            onSelect = { selectedIds = it.toList() },
            onOpen = onOpen,
        )
    return ListSelection(selected, { selectedIds = emptyList() }, gestures)
}
