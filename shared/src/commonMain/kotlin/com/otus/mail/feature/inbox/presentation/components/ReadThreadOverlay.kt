package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.otus.designsystem.components.ThreadEntry
import com.otus.designsystem.components.ThreadList
import com.otus.designsystem.components.Unfold
import com.otus.designsystem.components.UnfoldingStripCard
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.PageTokens
import com.otus.designsystem.theme.SheetTokens
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.OwnAddresses
import com.otus.mail.feature.inbox.domain.isOwn
import com.otus.mail.feature.inbox.presentation.ReadState
import com.otus.mail.feature.inbox.presentation.read.shortLabel
import kotlinx.datetime.LocalDate

/**
 * The thread strip grown into the conversation's messages, newest first, with the page at
 * [current] marked, as far as [unfold] has opened it. Choosing one runs [onSelect]; the header runs
 * [onFold].
 */
@Composable
internal fun ReadThreadList(
    entries: List<ThreadEntry>,
    current: Int,
    maxHeight: Dp,
    unfold: Unfold,
    onSelect: (Int) -> Unit,
    onFold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UnfoldingStripCard(unfold, modifier) {
        ThreadList("${entries.size} messages", entries, current, onSelect, onFold, maxHeight = maxHeight)
    }
}

/**
 * The message list grown from the thread strip over the page at [current], which dims behind it
 * with the action bar. It sits where the strip does, above the bar's [padding], and grows as
 * [unfold] opens, with the finger while it drags. Choosing a message runs [onSelect] and folds it,
 * as do its header, a tap outside, and dragging it down.
 */
@Composable
internal fun ReadThreadOverlay(
    state: ReadState,
    current: Int,
    today: LocalDate,
    own: OwnAddresses,
    padding: PaddingValues,
    unfold: Unfold,
    onSelect: (Int) -> Unit,
) {
    val fold = { state.threadOpen = false }
    LaunchedEffect(state.threadOpen) { unfold.animateTo(state.threadOpen) }
    val drag = rememberUnfoldDrag(unfold) { state.threadOpen = it }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (unfold.fraction > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(OtusColors.scrim.copy(alpha = SheetTokens.SCRIM_ALPHA * unfold.fraction))
                    .semantics { contentDescription = "Fold messages" }
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = fold),
            )
        }
        ReadThreadList(
            state.pages.map { page -> threadEntry(page, own, today) },
            current,
            maxHeight * LIST_HEIGHT_SHARE,
            unfold,
            onSelect = {
                onSelect(it)
                fold()
            },
            onFold = fold,
            Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(padding)
                .padding(PageTokens.pageInset)
                .then(rememberFoldSwipe(drag)),
        )
    }
}

/** One message of the grown list: "You" for the user's own, else the sender, with its date and preview. */
private fun threadEntry(
    page: MailMessage,
    own: OwnAddresses,
    today: LocalDate,
): ThreadEntry {
    val name = if (page.isOwn(own, page.sender)) "You" else page.sender.name.ifBlank { page.sender.address }
    return ThreadEntry(
        name,
        page.sender.chipAvatar,
        page.received.shortLabel(today),
        page.preview.ifBlank { page.body },
    )
}

/** How much of the page the grown message list may cover before it scrolls. */
private const val LIST_HEIGHT_SHARE = 0.7f
