package com.otusfoss.mail.feature.compose.presentation.components

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
import com.otusfoss.core.designsystem.mail.compose.components.RichTextBody
import com.otusfoss.core.designsystem.mail.compose.elements.QuoteChip
import com.otusfoss.core.designsystem.theme.colors.OtusColors
import com.otusfoss.core.designsystem.theme.tokens.layout.PageTokens
import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.feature.compose.presentation.ComposeState
import com.otusfoss.mail.feature.compose.presentation.RecipientField

/**
 * The header lines, then the body filling the rest of the page, the attached files, and a
 * forward's original collapsed behind a chip; all scroll together. A new message or forward starts in To.
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
            // A reply's thread shows above the bar instead; only a forward carries its original here.
            val quote =
                state.draft.quote
                    .takeIf { state.thread == null }
                    .orEmpty()
            // The body fills the page only while nothing follows it, so files and a quote stay in view.
            val alone = quote.isEmpty() && state.draft.attachments.isEmpty()
            RichTextBody(
                state.body,
                placeholder = "Write your message",
                Modifier.heightIn(min = if (alone) bodyHeight.coerceAtLeast(0.dp) else 0.dp),
                images = { state.attachments.image(state.draft.attachments, it) },
            )
            ComposeAttachmentList(state.draft.attachments, state.attachments)
            if (quote.isNotEmpty()) QuotedOriginal(quote)
        }
    }
}

@Composable
private fun QuotedOriginal(quote: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    if (expanded) {
        Text(quote, Modifier.padding(horizontal = PageTokens.textInset), color = OtusColors.text2)
    } else {
        QuoteChip(FORWARD_LABEL, { expanded = true })
    }
}

/** A reply starts in its body, so only new messages and forwards start on To. */
private val ComposeRequest.startsInTo: Boolean
    get() =
        when (this) {
            is ComposeRequest.New, is ComposeRequest.Forward -> true
            is ComposeRequest.Reply, is ComposeRequest.FollowUp, is ComposeRequest.Resume -> false
        }

private const val FORWARD_LABEL = "Forwarded message"
