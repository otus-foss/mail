package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.components.EmptyState
import com.otus.designsystem.components.RowSelection
import com.otus.designsystem.components.SwipeActions
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_inbox
import com.otus.designsystem.resources.symbol_search
import com.otus.designsystem.theme.InboxTokens
import com.otus.designsystem.theme.motionTween
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.InboxStatus

@Composable
internal fun InboxBody(
    messages: List<MailMessage>,
    listState: LazyListState,
    dateFormatter: DateFormatter,
    gestures: RowGestures,
    padding: PaddingValues,
    emptyContent: @Composable () -> Unit,
) {
    if (messages.isEmpty()) {
        Box(Modifier.padding(horizontal = InboxTokens.pageInset)) { emptyContent() }
    } else {
        // Account names tell rows apart only when the list mixes accounts.
        val mixedAccounts = remember(messages) { messages.distinctBy { it.location.accountId }.size > 1 }
        // Rows reach the screen edges, so a swipe's color does too; the inset aligns their content.
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding =
                PaddingValues(
                    top = InboxTokens.pageInset,
                    bottom =
                        InboxTokens.pageInset + padding.calculateBottomPadding(),
                ),
        ) {
            itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                val selectedIds = gestures.selectedIds
                val selected = message.id in selectedIds

                fun neighborSelected(offset: Int) = selected && messages.getOrNull(index + offset)?.id in selectedIds
                val selection =
                    RowSelection(
                        selected,
                        neighborSelected(-1),
                        neighborSelected(1),
                    ).takeIf { selectedIds.isNotEmpty() }
                val actions = gestures.actions
                val swipes = actions?.behavior?.invoke()
                SwipeActions(
                    startToEnd = swipes?.let { actions.swipeAction(message, it.startToEnd) },
                    endToStart = swipes?.let { actions.swipeAction(message, it.endToStart) },
                    modifier = Modifier.animateItem(motionTween(), motionTween(), motionTween()),
                ) {
                    Box(Modifier.padding(horizontal = InboxTokens.pageInset)) {
                        MessageRow(
                            message,
                            dateFormatter,
                            mixedAccounts,
                            selection = selection,
                            onLongClick = { gestures.onToggle(message) },
                        ) { gestures.tap(message) }
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
    status: InboxStatus,
    onReset: () -> Unit,
    onRefresh: () -> Unit,
) {
    if (status.loading) {
        EmptyState("Loading mail", "Fetching messages from your accounts.", Res.drawable.symbol_inbox)
        return
    }
    if (!hasMessages && status.notice != null) {
        EmptyState(
            "Couldn't load mail",
            status.notice,
            Res.drawable.symbol_inbox,
            actionLabel = "Try again",
            onAction = onRefresh,
        )
        return
    }
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
                status.connected -> "New messages will appear here."
                else -> "Connect an account in Settings to see your mail."
            },
        icon = if (searching) Res.drawable.symbol_search else Res.drawable.symbol_inbox,
        actionLabel = "Clear search and filters".takeIf { filtered },
        onAction = onReset,
    )
}
