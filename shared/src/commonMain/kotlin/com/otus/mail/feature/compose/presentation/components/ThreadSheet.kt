package com.otus.mail.feature.compose.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.dateformat.DateFormatter
import com.otus.dateformat.formatNamedDay
import com.otus.designsystem.mail.read.components.ThreadEntry
import com.otus.designsystem.mail.read.components.ThreadMessageRow
import com.otus.designsystem.mail.read.components.ThreadStrip
import com.otus.designsystem.shared.controls.components.SegmentedToggle
import com.otus.designsystem.shared.overlays.components.SheetFrame
import com.otus.designsystem.shared.overlays.elements.sheetBodyInset
import com.otus.designsystem.shared.rows.components.RowGroup
import com.otus.designsystem.shared.rows.elements.Caption
import com.otus.designsystem.shared.rows.elements.listCaptionInset
import com.otus.designsystem.theme.tokens.overlays.SheetTokens
import com.otus.mail.core.messages.domain.shownName
import com.otus.mail.core.messages.presentation.components.chipAvatar
import com.otus.mail.feature.compose.domain.QuoteMode
import com.otus.mail.feature.compose.domain.ThreadMessage
import com.otus.mail.feature.compose.presentation.ReplyThread
import com.otus.settings.presentation.components.LocalDateFormatter

/** The earlier messages of a reply's thread above its bar; tapping it runs [onOpen]. */
@Composable
internal fun ReplyThreadStrip(
    thread: ReplyThread,
    onOpen: () -> Unit,
) {
    val dates = LocalDateFormatter.current
    val entries = thread.messages.map { it.entry(dates) }
    val latest = thread.messages.first()
    ThreadStrip(
        entries,
        summary = "${entries.size} earlier messages",
        detail = "Latest: ${latest.sender.shownName}, ${dates.formatNamedDay(latest.date)}",
        onOpen = onOpen,
    )
}

/** Every earlier message of the thread, and how much of it the reply quotes. */
@Composable
internal fun ThreadSheet(
    thread: ReplyThread,
    onDismiss: () -> Unit,
) {
    SheetFrame("Earlier in this thread", onDismiss, done = null, closeButton = true) {
        Column(
            Modifier.sheetBodyInset(),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            val dates = LocalDateFormatter.current
            RowGroup(thread.messages.map { message -> { ThreadMessageRow(message.entry(dates)) } })
            Caption("Quote in your reply", Modifier.listCaptionInset())
            SegmentedToggle(
                QuoteMode.entries.map { it.label },
                QuoteMode.entries.indexOf(thread.quoteMode),
                { thread.quote(QuoteMode.entries[it]) },
            )
        }
    }
}

private fun ThreadMessage.entry(dates: DateFormatter): ThreadEntry =
    ThreadEntry(sender.shownName, sender.chipAvatar, dates.formatNamedDay(date), preview)

private val QuoteMode.label: String
    get() =
        when (this) {
            QuoteMode.Thread -> "Whole thread"
            QuoteMode.Latest -> "Latest only"
            QuoteMode.None -> "None"
        }
