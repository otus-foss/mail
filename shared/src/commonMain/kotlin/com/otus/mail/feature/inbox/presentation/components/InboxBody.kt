package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_inbox
import com.otus.designsystem.resources.symbol_search
import com.otus.designsystem.shared.feedback.components.EmptyState
import com.otus.designsystem.shared.feedback.elements.RefreshIndicator
import com.otus.designsystem.shared.rows.components.rowSelection
import com.otus.designsystem.shared.swipe.components.SwipeActions
import com.otus.designsystem.theme.colors.OtusColors
import com.otus.designsystem.theme.motion.motionTween
import com.otus.designsystem.theme.tokens.rows.EntryTokens
import com.otus.designsystem.theme.tokens.rows.InboxTokens
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.ConversationRowModel
import com.otus.mail.feature.inbox.presentation.InboxStatus
import com.otus.mail.feature.inbox.presentation.ListFrame
import com.otus.mail.feature.inbox.presentation.conversationRows

@Composable
internal fun InboxBody(
    messages: List<MailMessage>,
    listState: LazyListState,
    dateFormatter: DateFormatter,
    gestures: RowGestures,
    frame: ListFrame,
    status: InboxStatus,
    emptyContent: @Composable () -> Unit,
) {
    val mixed = rememberMixedAddresses(messages, frame.look.own, frame.narrowed)
    if (messages.isEmpty()) {
        Box(
            Modifier.padding(start = InboxTokens.pageInset, top = frame.topInset, end = InboxTokens.pageInset),
        ) { emptyContent() }
    } else {
        ConversationList(messages, listState, dateFormatter, gestures, frame, status, mixed)
    }
}

@Composable
private fun ConversationList(
    messages: List<MailMessage>,
    listState: LazyListState,
    dateFormatter: DateFormatter,
    gestures: RowGestures,
    frame: ListFrame,
    status: InboxStatus,
    mixed: Boolean,
) {
    val today = mailToday()
    val rows =
        remember(messages, frame.look, dateFormatter, today, mixed) {
            conversationRows(messages, frame.look, dateFormatter, today, mixed)
        }
    val ids = remember(messages) { messages.map { it.id } }
    val currentMessages by rememberUpdatedState(messages)
    val currentGestures by rememberUpdatedState(gestures)
    LazyColumn(
        Modifier.fillMaxSize().dragToSelect(
            listState,
            ids = { currentMessages.map { it.id } },
            selectedIds = { currentGestures.selectedIds },
        ) { currentGestures.onSelect(it) },
        state = listState,
        contentPadding =
            PaddingValues(
                top = InboxTokens.pageInset + frame.topInset,
                bottom = InboxTokens.pageInset + frame.bottomInset,
            ),
        verticalArrangement = Arrangement.spacedBy(EntryTokens.gap),
    ) {
        listNotices(status, frame)
        itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
            val model = rows[message.id] ?: return@itemsIndexed
            ConversationItem(message, model, ids, index, gestures)
        }
        if (status.loadingMore) loadingMoreFooter()
    }
}

private fun LazyListScope.listNotices(
    status: InboxStatus,
    frame: ListFrame,
) {
    val notice = status.notice
    if (notice != null) {
        item(key = NOTICE_KEY) {
            Text(
                notice,
                Modifier.padding(horizontal = InboxTokens.pageInset + InboxTokens.rowPadding),
                color = OtusColors.text2,
                fontSize = InboxTokens.metadataSize,
            )
        }
    }
    val nudge = frame.nudge
    if (nudge != null) {
        item(key = NUDGE_KEY) {
            ListNudgeCard(nudge, Modifier.animateItem(motionTween(), motionTween(), motionTween()))
        }
    }
}

/** The pull-to-refresh spinner under the last row while the next page loads. */
private fun LazyListScope.loadingMoreFooter() {
    item(key = LOADING_MORE_KEY) {
        Box(Modifier.fillMaxWidth().padding(InboxTokens.pageInset), contentAlignment = Alignment.Center) {
            RefreshIndicator(progress = { 1f }, refreshing = true)
        }
    }
}

/** A conversation's row with its swipes, joined to selected neighbors while the list is selecting. */
@Composable
private fun LazyItemScope.ConversationItem(
    message: MailMessage,
    model: ConversationRowModel,
    ids: List<String>,
    index: Int,
    gestures: RowGestures,
) {
    val selection = rowSelection(ids, index, gestures.selectedIds)
    val actions = gestures.actions
    val swipes = actions?.behavior?.invoke()
    SwipeActions(
        startToEnd = swipes?.let { actions.swipeAction(message, it.startToEnd) },
        endToStart = swipes?.let { actions.swipeAction(message, it.endToStart) },
        // Inset outside the swipe, so its accent card sits exactly behind the row's card.
        modifier =
            Modifier
                .animateItem(motionTween(), motionTween(), motionTween())
                .padding(horizontal = InboxTokens.pageInset),
    ) {
        ConversationRow(
            message,
            model,
            selection = selection,
            onLongClick = { gestures.onToggle(message) },
        ) { gestures.tap(message) }
    }
}

private const val NUDGE_KEY = "folder-color-nudge"
private const val NOTICE_KEY = "load-notice"
private const val LOADING_MORE_KEY = "loading-more"

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
    if (status.loading || status.loadingMore) {
        // A filtered list keeps searching later pages, so an empty one is not a final answer yet.
        if (filtered) {
            EmptyState("Searching mail", null, Res.drawable.symbol_search)
        } else {
            EmptyState("Loading mail", "Fetching messages from your accounts.", Res.drawable.symbol_inbox)
        }
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
                filtered -> "No matching mail"
                else -> emptyTitle
            },
        description =
            when {
                hasMessages -> null
                status.connected -> "New messages will appear here."
                else -> "Connect an account in Settings to see your mail."
            },
        icon = if (searching) Res.drawable.symbol_search else Res.drawable.symbol_inbox,
        actionLabel = "Clear search and filters".takeIf { filtered },
        onAction = onReset,
    )
}
