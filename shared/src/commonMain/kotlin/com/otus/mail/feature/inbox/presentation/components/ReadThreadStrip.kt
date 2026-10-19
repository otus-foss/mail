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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import com.otus.designsystem.components.PositionPage
import com.otus.designsystem.components.SlidingPositionStrip
import com.otus.designsystem.components.StripCard
import com.otus.designsystem.components.ThreadEntry
import com.otus.designsystem.components.ThreadList
import com.otus.designsystem.elements.FirstFit
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
 * The thread strip, newest first: "Latest" on the newest page, else the sender, with the position
 * and date of each page that [pageAt] gives, [current] on screen of [count]. Its card stays while
 * each page's content slides inside it as [slide] turns the pages, and a drag on it turns them too.
 * Its end names the conversation's other [folders], and a flag when it is [flagged] and the list
 * gave no folder context. Screen readers hear the sender, the position, the date, and the folders,
 * such as "Grace Hopper, message 1 of 6, 12 Oct, also in Invoices".
 */
@Composable
internal fun ReadThreadStrip(
    pageAt: (Int) -> ThreadPosition,
    current: Int,
    count: Int,
    folders: ShownFolders,
    flagged: Boolean,
    onClick: () -> Unit,
    slide: StripSlide,
) {
    val flag = flagged && !folders.also
    SlidingPositionStrip(
        count,
        current,
        slide.position,
        { index ->
            val position = pageAt(index)
            val page = position.page
            val sender = page.sender.name.ifBlank { page.sender.address }
            PositionPage(
                NamedAvatar(if (position.current == 0) "Latest" else sender, page.sender.chipAvatar),
                "${position.current + 1}/${position.count} · ${position.date}",
                spokenPosition(sender, position, folders, flag),
            )
        },
        onClick,
        slide.swipe,
    ) { FolderSlot(folders, flag) }
}

private fun spokenPosition(
    sender: String,
    position: ThreadPosition,
    folders: ShownFolders,
    flagged: Boolean,
): String =
    buildList {
        add("$sender, message ${position.current + 1} of ${position.count}, ${position.date}")
        if (folders.folders.isNotEmpty()) {
            val names = folders.folders.joinToString { it.name }
            add(if (folders.also) "also in $names" else "in $names")
        }
        if (flagged) add("flagged")
    }.joinToString()

/**
 * The short folder hint at the strip's end: "also", then one folder by name or stacked icons and a
 * count. Where that does not fit beside the position it drops "also", then a count's words; one
 * folder's name shortens instead.
 */
@Composable
private fun FolderSlot(
    folders: ShownFolders,
    flag: Boolean,
) {
    val shown = folders.folders
    if (shown.isEmpty() && !flag) return
    // The strip has little room, so a folder shows by its own name; the sheet shows full paths.
    val names = shown.map { folderName(it, listOf(it.name)) }
    val also = folders.also && shown.isNotEmpty()
    val stack = @Composable { if (shown.isNotEmpty()) FolderStack(names.map { if (it.system) null else it.color }) }
    val label =
        @Composable {
            if (shown.size == 1) {
                FolderHint(names, fontSize = ReadTokens.summarySize)
            } else {
                stack()
                if (shown.size > 1) SlotText("${shown.size} folders", OtusColors.text1)
            }
        }
    val full =
        @Composable {
            if (also) SlotText("also")
            label()
        }
    val options: List<@Composable () -> Unit> =
        if (shown.size > 1) {
            listOf({ SlotRow(flag, full) }, { SlotRow(flag, label) }, { SlotRow(flag, stack) })
        } else {
            listOf({ SlotRow(flag, full) }, { SlotRow(flag, label) })
        }
    FirstFit(options)
}

/** One version of the folder slot, after the flag when the conversation is [flagged]. */
@Composable
private fun SlotRow(
    flagged: Boolean,
    content: @Composable () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ReadTokens.glyphGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (flagged) {
            CompositionLocalProvider(LocalContentColor provides OtusColors.text2) {
                Symbol(Res.drawable.symbol_flag, size = ReadTokens.folderGlyphSize)
            }
        }
        content()
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
 * The message list grown from the thread strip over the page at [current], which dims behind it
 * with the action bar. It sits where the strip does, above the bar's [padding]; choosing a message
 * runs [onSelect] and folds it, as do its header and a tap outside.
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
                    .safeDrawingPadding()
                    .padding(padding)
                    .padding(PageTokens.pageInset)
                    .animateEnterExit(enter = expandVertically(motionTween(), expandFrom = Alignment.Bottom)),
            )
        }
    }
}

/** How much of the page the grown message list may cover before it scrolls. */
private const val LIST_HEIGHT_SHARE = 0.7f
