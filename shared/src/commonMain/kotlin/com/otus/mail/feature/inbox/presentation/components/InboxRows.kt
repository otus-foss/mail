package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.OpenRequest

/** The open message, the selected rows, and the row gestures that change them; [selectAll] selects the rows given. */
internal class InboxRows(
    val opened: MailMessage?,
    val selected: List<MailMessage>,
    val gestures: RowGestures,
    val clearSelection: () -> Unit,
    val selectAll: (List<MailMessage>) -> Unit,
    val close: () -> Unit,
)

/**
 * Which of [messages] is open or selected; system back clears a selection. A tapped draft goes to
 * [openDraft] instead of opening. [opening] opens its row as a tap would, marking it read when the
 * saved behavior asks.
 */
@Composable
internal fun rememberInboxRows(
    messages: List<MailMessage>,
    actions: MessageActions?,
    openDraft: (MailMessage) -> Unit,
    opening: OpenRequest? = null,
): InboxRows {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    val selected = remember(messages, selectedIds) { messages.filter { it.id in selectedIds } }
    val clearSelection = { selectedIds = emptyList() }
    NavigationBackHandler(
        rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = selected.isNotEmpty(),
        onBackCompleted = clearSelection,
    )
    LaunchedEffect(opening) {
        val request = opening ?: return@LaunchedEffect
        val row = messages.firstOrNull { it.id == request.id }
        if (row != null) actions?.opened(row)
        openId = request.id
        request.taken()
    }
    val gestures =
        rowGestures(
            actions,
            selected,
            onToggle = { selectedIds = selectedIds.toggled(it.id) },
            onSelect = { selectedIds = it.toList() },
        ) { if (it.attributes.draft) openDraft(it) else openId = it.id }
    return InboxRows(
        messages.firstOrNull { it.id == openId },
        selected,
        gestures,
        clearSelection,
        selectAll = { rows -> selectedIds = rows.map { it.id } },
    ) { openId = null }
}

/** The selection the control panel shows while rows are selected, with Select all taking [shown]; null otherwise. */
internal fun InboxRows.panelSelection(shown: List<MailMessage>): PanelSelection? =
    selected.size.takeIf { it > 0 }?.let { count -> PanelSelection(count, clearSelection) { selectAll(shown) } }
