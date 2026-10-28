package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.designsystem.mail.read.components.ThreadEntry
import com.otus.designsystem.mail.read.components.ThreadMessageRow
import com.otus.designsystem.mail.read.components.ThreadStrip
import com.otus.designsystem.shared.controls.components.SegmentedToggle
import com.otus.designsystem.shared.overlays.components.ClosableSheet
import com.otus.designsystem.shared.rows.components.RowGroup
import com.otus.designsystem.shared.selection.components.ListCaption
import com.otus.designsystem.theme.tokens.overlays.SheetTokens
import com.otus.mail.feature.inbox.domain.QuoteMode
import com.otus.mail.feature.inbox.domain.ThreadMessage
import com.otus.mail.feature.inbox.presentation.ReplyThread

/** The earlier messages of a reply's thread above its bar; tapping it runs [onOpen]. */
@Composable
internal fun ReplyThreadStrip(
    thread: ReplyThread,
    onOpen: () -> Unit,
) {
    val entries = thread.messages.map { it.entry() }
    val latest = thread.messages.first()
    ThreadStrip(
        entries,
        summary = "${entries.size} earlier messages",
        detail = "Latest: ${latest.sender.name.ifBlank { latest.sender.address }}, ${latest.date.dayAndMonth}",
        onOpen = onOpen,
    )
}

/** Every earlier message of the thread, and how much of it the reply quotes. */
@Composable
internal fun ThreadSheet(
    thread: ReplyThread,
    onDismiss: () -> Unit,
) {
    ClosableSheet("Earlier in this thread", onDismiss) {
        Column(
            Modifier.padding(start = SheetTokens.inset, end = SheetTokens.inset, bottom = SheetTokens.inset),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            RowGroup(thread.messages.map { message -> { ThreadMessageRow(message.entry()) } })
            ListCaption("Quote in your reply")
            SegmentedToggle(
                QuoteMode.entries.map { it.label },
                QuoteMode.entries.indexOf(thread.quoteMode),
                { thread.quote(QuoteMode.entries[it]) },
            )
        }
    }
}

private fun ThreadMessage.entry(): ThreadEntry =
    ThreadEntry(sender.name.ifBlank { sender.address }, sender.chipAvatar(), date.dayAndMonth, preview)

private val QuoteMode.label: String
    get() =
        when (this) {
            QuoteMode.Thread -> "Whole thread"
            QuoteMode.Latest -> "Latest only"
            QuoteMode.None -> "None"
        }
