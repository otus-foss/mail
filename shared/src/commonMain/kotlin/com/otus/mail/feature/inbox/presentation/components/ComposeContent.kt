package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.otus.designsystem.elements.QuoteChip
import com.otus.designsystem.elements.RichTextBody
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.PageTokens
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.OriginalMessage
import com.otus.mail.feature.inbox.presentation.ComposeState
import com.otus.mail.feature.inbox.presentation.RecipientField
import kotlinx.datetime.LocalDate

/**
 * The header lines, then the body filling the rest of the page, then the quoted original
 * collapsed behind a chip; all scroll together. A new message or forward starts in To.
 */
@Composable
internal fun ComposeContent(
    state: ComposeState,
    shown: Set<RecipientField>,
    modifier: Modifier,
    padding: PaddingValues,
) {
    val focus = remember { FocusRequester() }
    var fieldsHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    LaunchedEffect(focus) { if (state.request.startsInTo) focus.requestFocus() }
    BoxWithConstraints(modifier) {
        val bodyHeight = maxHeight - with(density) { fieldsHeight.toDp() } - padding.calculateBottomPadding()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)) {
            ComposeFields(state, shown, focus, Modifier.onSizeChanged { fieldsHeight = it.height })
            val quote = state.draft.quote
            RichTextBody(
                state.body,
                placeholder = "Write your message",
                Modifier.heightIn(min = if (quote.isEmpty()) bodyHeight.coerceAtLeast(0.dp) else 0.dp),
            )
            if (quote.isNotEmpty()) QuotedOriginal(quote, state.request.quoteLabel)
        }
    }
}

@Composable
private fun QuotedOriginal(
    quote: String,
    label: String,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    if (expanded) {
        Text(quote, Modifier.padding(horizontal = PageTokens.textInset), color = OtusColors.text2)
    } else {
        QuoteChip(label, { expanded = true })
    }
}

/** A reply starts in its body, so only new messages and forwards start on To. */
private val ComposeRequest.startsInTo: Boolean
    get() =
        when (this) {
            is ComposeRequest.New, is ComposeRequest.Forward -> true
            is ComposeRequest.Reply, is ComposeRequest.FollowUp, is ComposeRequest.Resume -> false
        }

/** What the collapsed quote says, such as "Grace Hopper wrote on 11 Oct". */
private val ComposeRequest.quoteLabel: String
    get() =
        when (this) {
            is ComposeRequest.Reply -> original.wroteLabel
            is ComposeRequest.FollowUp -> original.wroteLabel
            is ComposeRequest.Forward -> "Forwarded message"
            is ComposeRequest.Resume -> from.quoteLabel
            is ComposeRequest.New -> ""
        }

private val OriginalMessage.wroteLabel: String
    get() {
        val name = from.name.ifBlank { from.address }
        val day = runCatching { LocalDate.parse(sentLabel.substringBefore(' ')) }.getOrNull()
        return if (day ==
            null
        ) {
            "$name wrote"
        } else {
            "$name wrote on ${day.day} ${day.month.name.take(
                MONTH_ABBREVIATION,
            ).lowercase().replaceFirstChar(Char::uppercase)}"
        }
    }

private const val MONTH_ABBREVIATION = 3
