package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.components.EmptyState
import com.otus.designsystem.components.SwipeActions
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_inbox
import com.otus.designsystem.resources.symbol_search
import com.otus.designsystem.theme.InboxTokens
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.MessageActions

@Composable
fun InboxBody(
    messages: List<MailMessage>,
    listState: LazyListState,
    dateFormatter: DateFormatter,
    onOpen: (MailMessage) -> Unit,
    actions: MessageActions?,
    emptyContent: @Composable () -> Unit,
) {
    if (messages.isEmpty()) {
        Box(Modifier.padding(horizontal = InboxTokens.pageInset)) { emptyContent() }
    } else {
        // Rows reach the screen edges, so a swipe's color does too; the inset aligns their content.
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(vertical = InboxTokens.pageInset),
        ) {
            items(messages, key = { it.id }) { message ->
                val swipes = actions?.behavior?.invoke()
                SwipeActions(
                    startToEnd = swipes?.let { actions.swipeAction(message, it.startToEnd) },
                    endToStart = swipes?.let { actions.swipeAction(message, it.endToStart) },
                    modifier = Modifier.animateItem(),
                ) {
                    Box(Modifier.padding(horizontal = InboxTokens.pageInset)) {
                        MessageRow(message, dateFormatter) { onOpen(message) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun InboxEmptyState(
    emptyTitle: String,
    searching: Boolean,
    hasMessages: Boolean,
    filtered: Boolean,
    connected: Boolean,
    onReset: () -> Unit,
) {
    EmptyState(
        title =
            when {
                searching && !hasMessages -> "Nothing to search yet"
                filtered -> "No matching messages"
                else -> emptyTitle
            },
        description =
            when {
                hasMessages -> "Try another search or clear your filters."
                connected -> "New messages will appear here."
                else -> "Connect an account in Settings to see your mail."
            },
        icon = if (searching) Res.drawable.symbol_search else Res.drawable.symbol_inbox,
        actionLabel = "Clear search and filters".takeIf { filtered },
        onAction = onReset,
    )
}
