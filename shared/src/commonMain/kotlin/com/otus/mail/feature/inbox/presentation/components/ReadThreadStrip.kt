package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.otus.designsystem.components.FolderHint
import com.otus.designsystem.components.NamedAvatar
import com.otus.designsystem.components.PositionStrip
import com.otus.designsystem.components.StripCard
import com.otus.designsystem.components.ThreadEntry
import com.otus.designsystem.components.ThreadList
import com.otus.designsystem.elements.FolderStack
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_flag
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.PageTokens
import com.otus.designsystem.theme.ReadTokens
import com.otus.designsystem.theme.SheetTokens
import com.otus.designsystem.theme.motionTween
import com.otus.designsystem.theme.panelTextStyle
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.OwnAddresses
import com.otus.mail.feature.inbox.domain.ShownFolders
import com.otus.mail.feature.inbox.domain.isOwn
import com.otus.mail.feature.inbox.presentation.ReadState
import com.otus.mail.feature.inbox.presentation.read.shortLabel
import kotlinx.datetime.LocalDate

/**
 * The thread strip for the page at [current] of [pages], newest first: "Latest" on the newest
 * page, else the sender, with the position and [date]. Its end names the conversation's other
 * [folders], and a flag when it is [flagged] and the list gave no folder context.
 */
@Composable
internal fun ReadThreadStrip(
    pages: List<MailMessage>,
    current: Int,
    date: String,
    folders: ShownFolders,
    flagged: Boolean,
    onClick: () -> Unit,
) {
    val page = pages[current]
    val title = if (current == 0) "Latest" else page.sender.name.ifBlank { page.sender.address }
    PositionStrip(
        NamedAvatar(title, page.sender.chipAvatar),
        pages.size,
        current,
        "${current + 1}/${pages.size} · $date",
        onClick,
    ) { FolderSlot(folders, flagged && !folders.also) }
}

/** The short folder hint at the strip's end: "also", one name, or stacked icons and a count. */
@Composable
private fun RowScope.FolderSlot(
    folders: ShownFolders,
    flag: Boolean,
) {
    val shown = folders.folders
    if (shown.isEmpty() && !flag) return
    Row(
        Modifier.weight(SLOT_WEIGHT, fill = false),
        horizontalArrangement = Arrangement.spacedBy(ReadTokens.glyphGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (folders.also && shown.isNotEmpty()) SlotText("also")
        if (flag) {
            CompositionLocalProvider(LocalContentColor provides OtusColors.text2) {
                Symbol(Res.drawable.symbol_flag, size = ReadTokens.folderGlyphSize)
            }
        }
        if (shown.size == 1) {
            FolderHint(folderNames(shown), fontSize = ReadTokens.summarySize)
        } else if (shown.size > 1) {
            FolderStack(folderNames(shown).map { if (it.system) null else it.color })
            SlotText("${shown.size} folders", OtusColors.text1)
        }
    }
}

@Composable
private fun SlotText(
    text: String,
    color: Color = OtusColors.text3,
) {
    Text(
        text,
        style = panelTextStyle(),
        color = color,
        fontSize = ReadTokens.summarySize,
        maxLines = 1,
        softWrap = false,
    )
}

/**
 * The thread strip grown into the conversation's messages, newest first, with the page at
 * [current] marked. Choosing one runs [onSelect]; the header runs [onFold].
 */
@Composable
internal fun ReadThreadList(
    entries: List<ThreadEntry>,
    current: Int,
    maxHeight: Dp,
    onSelect: (Int) -> Unit,
    onFold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StripCard(modifier) {
        ThreadList("${entries.size} messages", entries, current, onSelect, onFold, maxHeight = maxHeight)
    }
}

/**
 * The message list grown from the thread strip over the page at [current], which dims behind it.
 * It sits where the strip does, above the bar's [padding]; choosing a message runs [onSelect] and
 * folds it, as do its header and a tap outside.
 */
@Composable
internal fun ReadThreadOverlay(
    state: ReadState,
    current: Int,
    today: LocalDate,
    own: OwnAddresses,
    padding: PaddingValues,
    onSelect: (Int) -> Unit,
) {
    val fold = { state.threadOpen = false }
    AnimatedVisibility(state.threadOpen, enter = fadeIn(motionTween()), exit = fadeOut(motionTween())) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(OtusColors.scrim.copy(alpha = SheetTokens.SCRIM_ALPHA))
                    .semantics { contentDescription = "Fold messages" }
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = fold),
            )
            val entries =
                state.pages.map { page ->
                    val name =
                        if (page.isOwn(
                                own,
                                page.sender,
                            )
                        ) {
                            "You"
                        } else {
                            page.sender.name.ifBlank { page.sender.address }
                        }
                    ThreadEntry(
                        name,
                        page.sender.chipAvatar,
                        page.received.shortLabel(today),
                        page.preview.ifBlank { page.body },
                    )
                }
            ReadThreadList(
                entries,
                current,
                maxHeight * LIST_HEIGHT_SHARE,
                onSelect = {
                    onSelect(it)
                    fold()
                },
                onFold = fold,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(padding)
                    .padding(PageTokens.pageInset)
                    .animateEnterExit(enter = expandVertically(motionTween(), expandFrom = Alignment.Bottom)),
            )
        }
    }
}

/** How much of the page the grown message list may cover before it scrolls. */
private const val LIST_HEIGHT_SHARE = 0.7f

/** Only so much of the strip's width goes to folders, so the position beside them never shortens. */
private const val SLOT_WEIGHT = 0.6f
