package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.otus.designsystem.components.ActionBarHost
import com.otus.designsystem.components.LocalSheetPull
import com.otus.designsystem.components.OptionsItem
import com.otus.designsystem.theme.InboxTokens
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.PageTokens
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.withContent
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.ReadContext
import com.otus.mail.feature.inbox.presentation.ReadLaterChange
import com.otus.mail.feature.inbox.presentation.ReadState
import com.otus.mail.feature.inbox.presentation.components.ReadActionBar
import com.otus.mail.feature.inbox.presentation.components.ReadBottomStack
import com.otus.mail.feature.inbox.presentation.components.ReadHeader
import com.otus.mail.feature.inbox.presentation.components.ReadHeaderActions
import com.otus.mail.feature.inbox.presentation.components.ReadPageLook
import com.otus.mail.feature.inbox.presentation.components.ReadPages
import com.otus.mail.feature.inbox.presentation.components.ReadParts
import com.otus.mail.feature.inbox.presentation.components.ReadSheets
import com.otus.mail.feature.inbox.presentation.components.ReadStripActions
import com.otus.mail.feature.inbox.presentation.components.ReadStripSource
import com.otus.mail.feature.inbox.presentation.components.ReadThreadOverlay
import com.otus.mail.feature.inbox.presentation.components.StripFolds
import com.otus.mail.feature.inbox.presentation.components.StripGestures
import com.otus.mail.feature.inbox.presentation.components.SwipeFeel
import com.otus.mail.feature.inbox.presentation.components.openLink
import com.otus.mail.feature.inbox.presentation.components.rememberFoldSwipe
import com.otus.mail.feature.inbox.presentation.components.rememberMoveChoice
import com.otus.mail.feature.inbox.presentation.components.rememberPageLook
import com.otus.mail.feature.inbox.presentation.components.rememberPageSwipe
import com.otus.mail.feature.inbox.presentation.components.rememberReadBar
import com.otus.mail.feature.inbox.presentation.components.rememberReadMenu
import com.otus.mail.feature.inbox.presentation.components.rememberStripFolds
import com.otus.mail.feature.inbox.presentation.components.rememberUnfoldDrag
import com.otus.mail.feature.inbox.presentation.read.fullLabel
import com.otus.mail.feature.inbox.presentation.read.shortLabel
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * An open message or conversation as a page over the list. Each message of the conversation is a
 * page, newest first. Filing acts on the whole conversation and closes the page first, while
 * replies and the ⋯ menu's message entries follow the page on screen. A [notice], such as a
 * refused mark, shows under the header. [cameFrom] is the folders the list shows the message from,
 * which the strips leave out; empty without a folder context. Back folds the message list, then the files, then the
 * details, then runs [onClose]. The details and the page on screen survive a recreated screen. Zen
 * mode leaves out the strips, the message list they open, and the images banner.
 */
@Composable
fun MessageScreen(
    message: MailMessage,
    actions: MessageActions?,
    notice: String?,
    context: InboxContext,
    cameFrom: Set<String>,
    onClose: () -> Unit,
) {
    val read = context.read ?: remember { ReadContext() }
    val state = rememberSaveable(message.id, saver = ReadState.saver(message)) { ReadState(message) }
    val pager = rememberPagerState { state.pages.size }
    LaunchedEffect(message.id) {
        state.showPages(read.pages.messages(message))?.let { pager.scrollToPage(it) }
    }
    LaunchedEffect(message.id) { state.muted = read.services.mute?.isMuted(message) }
    // The list's marks on the row settle the pages', such as Mark read from the list's bar.
    LaunchedEffect(message.attributes, state.loaded) { state.follow(message) }
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        onBackCompleted = { if (!state.back()) onClose() },
    )
    val moveChoice = rememberMoveChoice { onClose() }
    val parts = ReadParts(message, actions, context, read, state, moveChoice, onClose)
    val index = pager.currentPage.coerceIn(0, state.pages.lastIndex)
    val page = state.pages[index].withContent(parts.content(state.pages[index]))
    SideEffect { state.shownId = page.id }
    val look = rememberPageLook(parts)
    val bar = rememberReadBar(message, page, context.rowLook.own, actions, moveChoice, context.onCompose)
    val menu = rememberReadMenu(parts, look, bar, index)
    val today = remember { read.display.today() }
    var barPadding by remember { mutableStateOf(PaddingValues()) }
    val scope = rememberCoroutineScope()
    val folds = rememberStripFolds(state)
    Surface(Modifier.fillMaxSize(), color = OtusColors.bgRegular) {
        ActionBarHost(
            bar = bar.layout?.let { { ReadActionBar(bar, page, parts::perform) } },
            Modifier.safeDrawingPadding(),
        ) { padding ->
            SideEffect { barPadding = padding }
            ReadLayout(parts, ReadLayoutState(pager, index, look, menu, today, folds), notice, cameFrom, padding)
        }
        // Over the bar too, which dims with the page while the grown list sits above it.
        if (!read.display.zen()) {
            val source = ReadStripSource(parts.row, look.folders, cameFrom, today)
            ReadThreadOverlay(state, index, source, context.rowLook.own, barPadding, folds.thread) {
                scope.launch { pager.animateScrollToPage(it) }
            }
        }
    }
    // A drag on the folders strip pulls the folders sheet up with the finger.
    CompositionLocalProvider(LocalSheetPull provides folds.folderPull) { ReadSheets(parts, look) }
    actions?.let { moveChoice.Sheet(it) }
}

/**
 * Where the Read view's layout stands: the [pager] on the page at [index], how pages [look], the ⋯
 * [menu], the [today] dates are shown against, and how the bottom strips [folds] open.
 */
private class ReadLayoutState(
    val pager: PagerState,
    val index: Int,
    val look: ReadPageLook,
    val menu: List<OptionsItem>,
    val today: LocalDate,
    val folds: StripFolds,
)

/**
 * The pages over the whole screen, with the header floating over their top and the strips over
 * their bottom, above the bar's [padding]. A page leaves room for them around its text, so it
 * scrolls clear of them while using the space around them.
 */
@Composable
private fun ReadLayout(
    parts: ReadParts,
    layout: ReadLayoutState,
    notice: String?,
    cameFrom: Set<String>,
    padding: PaddingValues,
) {
    val state = parts.state
    val read = parts.read
    val row = parts.row
    val page = state.pages[layout.index]
    val uriHandler = LocalUriHandler.current
    val strips = remember(read) { ReadStripActions(read) { openLink(uriHandler::openUri, it) } }
    val date = page.received.shortLabel(layout.today)
    val zen = read.display.zen()
    var stripsHeight by remember { mutableStateOf(0.dp) }
    val bottomInset = padding.calculateBottomPadding() + PageTokens.pageInset * 2 + if (zen) 0.dp else stripsHeight
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(0.dp) }
    Box(Modifier.fillMaxSize()) {
        val swipe = rememberPageSwipe(layout.pager)
        ReadPages(
            state,
            layout.pager,
            layout.look,
            parts.context.loadContent,
            Modifier.fillMaxSize().then(swipe),
            PaddingValues(top = headerHeight, bottom = bottomInset),
        )
        Column(
            Modifier.padding(PageTokens.pageInset).onSizeChanged { size ->
                val height = with(density) { size.height.toDp() }
                // The room stays the tallest header, so folding the subject does not move the text.
                if (height > headerHeight) headerHeight = height
            },
        ) {
            ReadHeader(row.subject, page, date, page.received.fullLabel(), headerActions(parts, layout.menu))
            notice?.let { MessageNotice(it) }
        }
        if (!zen) {
            val pager = layout.pager
            val stripSwipe = rememberPageSwipe(pager, SwipeFeel.Strip)
            val threadSwipe =
                stripSwipe.then(rememberFoldSwipe(rememberUnfoldDrag(layout.folds.thread) { state.threadOpen = it }))
            val slide =
                StripGestures(threadSwipe, {
                    pager.currentPage + pager.currentPageOffsetFraction
                }, rememberFoldSwipe(layout.folds.folders))
            ReadBottomStack(
                state,
                layout.index,
                ReadStripSource(row, layout.look.folders, cameFrom, layout.today),
                strips,
                slide,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(padding)
                    .padding(PageTokens.pageInset),
                onFoldedHeight = { stripsHeight = it },
            )
        }
    }
}

private fun headerActions(
    parts: ReadParts,
    menu: List<OptionsItem>,
): ReadHeaderActions {
    val actions = parts.actions
    val read = parts.read
    return ReadHeaderActions(
        onBack = parts::close,
        senderOpen = parts.state.details,
        onSender = { parts.state.details = !parts.state.details },
        // A change that takes the message off this list closes the page first, as filing does.
        zen = read.display.zen(),
        onZen = {
            val on = !read.display.zen()
            if (on) parts.state.foldStrips()
            read.display.setZen(on)
        },
        readLater =
            actions?.readLater(parts.row)?.takeIf { actions.behavior().readLater }?.let { change ->
                if (!change.leaves) {
                    change
                } else {
                    ReadLaterChange(change.saves, leaves = true) {
                        parts.close()
                        change.apply()
                    }
                }
            },
        menu = menu,
    )
}

/** A notice, such as a refused mark, under the header. */
@Composable
private fun MessageNotice(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = PageTokens.textInset),
        color = OtusColors.text2,
        fontSize = InboxTokens.metadataSize,
    )
}
