package com.otusfoss.mail.feature.read.presentation.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.core.designsystem.mail.read.components.ThreadEntry
import com.otusfoss.core.designsystem.mail.read.components.ThreadList
import com.otusfoss.core.designsystem.shared.overlays.elements.Scrim
import com.otusfoss.core.designsystem.shared.rows.components.Unfold
import com.otusfoss.core.designsystem.shared.rows.components.UnfoldingStripCard
import com.otusfoss.core.designsystem.theme.tokens.layout.PageTokens
import com.otusfoss.core.designsystem.theme.tokens.rows.StripTokens
import com.otusfoss.core.settings.presentation.components.LocalDateFormatter
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.OwnAddresses
import com.otusfoss.mail.core.messages.domain.isOwn
import com.otusfoss.mail.core.messages.domain.shownName
import com.otusfoss.mail.core.messages.presentation.components.chipAvatar
import com.otusfoss.mail.core.messages.presentation.shortLabel
import com.otusfoss.mail.feature.read.presentation.ReadState
import kotlinx.datetime.LocalDate

/**
 * The message list grown from the thread strip over the page at [current], which dims behind it
 * with the action bar. It sits where the strip does, above the bar's [padding], and grows as
 * [unfold] opens, with the finger while it drags. The list blurs in over a still copy of the
 * [source]'s thread strip, which takes no gestures of its own. Choosing a message runs [onSelect]
 * and folds it, as do its header, a tap outside, and dragging it down.
 */
@Composable
internal fun ReadThreadOverlay(
    state: ReadState,
    current: Int,
    source: ReadStripSource,
    own: OwnAddresses,
    padding: PaddingValues,
    unfold: Unfold,
    onSelect: (Int) -> Unit,
) {
    val fold = { state.threadOpen = false }
    LaunchedEffect(state.threadOpen) { unfold.animateTo(state.threadOpen) }
    val drag = rememberUnfoldDrag(unfold) { state.threadOpen = it }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (unfold.fraction > 0f) Scrim("Fold messages", unfold.fraction, fold)
        val dates = LocalDateFormatter.current
        val entries = state.pages.map { page -> threadEntry(page, own, source.today, dates) }
        val still = StripGestures(Modifier, { null }, Modifier)
        // It grows from the thread strip's own height, so its top edge starts at the strip's.
        UnfoldingStripCard(
            unfold,
            StripTokens.height,
            { ConversationStrip(state, current, source, still) },
            Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(padding)
                .padding(PageTokens.pageInset)
                .then(rememberFoldSwipe(drag)),
        ) {
            val select = { index: Int ->
                onSelect(index)
                fold()
            }
            val height = maxHeight * LIST_HEIGHT_SHARE
            ThreadList("${entries.size} messages", entries, current, select, fold, maxHeight = height)
        }
    }
}

/** One message of the grown list: "You" for the user's own, else the sender, with its date and preview. */
private fun threadEntry(
    page: MailMessage,
    own: OwnAddresses,
    today: LocalDate,
    dates: DateFormatter,
): ThreadEntry {
    val name = if (page.isOwn(own, page.sender)) "You" else page.sender.shownName
    return ThreadEntry(
        name,
        page.sender.chipAvatar,
        dates.shortLabel(page.received, today),
        page.preview.ifBlank { page.body },
    )
}

/** How much of the page the grown message list may cover before it scrolls. */
private const val LIST_HEIGHT_SHARE = 0.7f
