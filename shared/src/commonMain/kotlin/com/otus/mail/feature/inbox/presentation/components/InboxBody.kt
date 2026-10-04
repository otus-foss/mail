package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.otus.mail.core.dateformat.DateFormatter
import com.otus.mail.designsystem.elements.Symbol
import com.otus.mail.designsystem.resources.Res
import com.otus.mail.designsystem.resources.symbol_inbox
import com.otus.mail.designsystem.resources.symbol_search
import com.otus.mail.designsystem.theme.InboxTokens
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.feature.inbox.domain.MailMessage

@Composable
fun InboxBody(
    messages: List<MailMessage>,
    listState: LazyListState,
    dateFormatter: DateFormatter,
    onOpen: (MailMessage) -> Unit,
    emptyContent: @Composable () -> Unit,
) {
    if (messages.isEmpty()) {
        emptyContent()
    } else {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(InboxTokens.pageInset)) {
            items(messages, key = { it.id }) { MessageRow(it, dateFormatter) { onOpen(it) } }
        }
    }
}

@Composable
internal fun InboxEmptyState(
    searching: Boolean,
    hasMessages: Boolean,
    filtered: Boolean,
    onReset: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(InboxTokens.contentInset), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(InboxTokens.itemGap),
        ) {
            Symbol(
                if (searching) Res.drawable.symbol_search else Res.drawable.symbol_inbox,
                size = InboxTokens.emptyIconSize,
            )
            Text(
                when {
                    searching && !hasMessages -> "Nothing to search yet"
                    filtered -> "No matching messages"
                    else -> "Your inbox is empty"
                },
                fontSize = InboxTokens.subjectSize,
                textAlign = TextAlign.Center,
            )
            Text(
                if (hasMessages) {
                    "Try another search or clear your filters."
                } else {
                    "Your messages will appear here once an account is connected."
                },
                color = OtusColors.text2,
                textAlign = TextAlign.Center,
            )
            if (filtered) TextButton(onClick = onReset) { Text("Clear search and filters") }
        }
    }
}
