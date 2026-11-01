package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Surface
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.otus.designsystem.shared.actionbar.components.ActionBarHost
import com.otus.designsystem.shared.overlays.components.LocalSheetPull
import com.otus.designsystem.shared.overlays.components.OptionsItem
import com.otus.designsystem.shared.pages.components.belowControlPanel
import com.otus.designsystem.shared.pages.elements.holdsSpace
import com.otus.designsystem.theme.colors.OtusColors
import com.otus.designsystem.theme.motion.MotionCurve
import com.otus.designsystem.theme.motion.MotionSpeed
import com.otus.designsystem.theme.motion.motionTween
import com.otus.designsystem.theme.tokens.layout.PageTokens
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.ReadQuickModule
import com.otus.mail.feature.inbox.domain.withContent
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.ReadContext
import com.otus.mail.feature.inbox.presentation.ReadState
import com.otus.mail.feature.inbox.presentation.components.ReadActionBar
import com.otus.mail.feature.inbox.presentation.components.ReadBottomStack
import com.otus.mail.feature.inbox.presentation.components.ReadPageLook
import com.otus.mail.feature.inbox.presentation.components.ReadPages
import com.otus.mail.feature.inbox.presentation.components.ReadParts
import com.otus.mail.feature.inbox.presentation.components.ReadQuickView
import com.otus.mail.feature.inbox.presentation.components.ReadSheets
import com.otus.mail.feature.inbox.presentation.components.ReadStripActions
import com.otus.mail.feature.inbox.presentation.components.ReadStripSource
import com.otus.mail.feature.inbox.presentation.components.ReadThreadOverlay
import com.otus.mail.feature.inbox.presentation.components.ReadTop
import com.otus.mail.feature.inbox.presentation.components.ReadTopText
import com.otus.mail.feature.inbox.presentation.components.StripFolds
import com.otus.mail.feature.inbox.presentation.components.StripGestures
import com.otus.mail.feature.inbox.presentation.components.SwipeFeel
import com.otus.mail.feature.inbox.presentation.components.hiddenWhen
import com.otus.mail.feature.inbox.presentation.components.openLink
import com.otus.mail.feature.inbox.presentation.components.readDetailsHeight
import com.otus.mail.feature.inbox.presentation.components.rememberFoldSwipe
import com.otus.mail.feature.inbox.presentation.components.rememberMoveChoice
import com.otus.mail.feature.inbox.presentation.components.rememberPageLook
import com.otus.mail.feature.inbox.presentation.components.rememberPageSwipe
import com.otus.mail.feature.inbox.presentation.components.rememberReadBar
import com.otus.mail.feature.inbox.presentation.components.rememberReadExpansion
import com.otus.mail.feature.inbox.presentation.components.rememberReadMenu
import com.otus.mail.feature.inbox.presentation.components.rememberStripFolds
import com.otus.mail.feature.inbox.presentation.components.rememberUnfoldDrag
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
    // A tapped message's page arrives like a swiped one, on the design system's easing.
    val turn = motionTween<Float>(MotionSpeed.Normal, MotionCurve.Enter)
    val folds = rememberStripFolds(state)
    val source = ReadStripSource(parts.row, look.folders, cameFrom, today)
    Surface(Modifier.fillMaxSize(), color = OtusColors.bgRegular) {
        ActionBarHost(
            bar = bar.layout?.let { { ReadActionBar(bar, page, parts::perform) } },
            // The control panel draws behind the status bar and pads itself.
            Modifier.belowControlPanel(),
        ) { padding ->
            SideEffect { barPadding = padding }
            ReadLayout(parts, ReadLayoutState(pager, index, look, menu, today, folds, source), notice, padding)
        }
        // Over the bar too, which dims with the page while the grown list sits above it.
        if (!read.display.zen()) {
            ReadThreadOverlay(state, index, source, context.rowLook.own, barPadding, folds.thread) {
                scope.launch { pager.animateScrollToPage(it, animationSpec = turn) }
            }
        }
    }
    // A drag on the folders strip pulls the folders sheet up with the finger.
    CompositionLocalProvider(LocalSheetPull provides folds.folderPull) { ReadSheets(parts, look) }
    actions?.let { moveChoice.Sheet(it) }
}

/**
 * Where the Read view's layout stands: the [pager] on the page at [index], how pages [look], the ⋯
 * [menu], the [today] dates are shown against, how the bottom strips [folds] open, and what they
 * show from [source].
 */
private class ReadLayoutState(
    val pager: PagerState,
    val index: Int,
    val look: ReadPageLook,
    val menu: List<OptionsItem>,
    val today: LocalDate,
    val folds: StripFolds,
    val source: ReadStripSource,
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
    padding: PaddingValues,
) {
    val state = parts.state
    val read = parts.read
    val page = state.pages[layout.index]
    val uriHandler = LocalUriHandler.current
    val strips = remember(read) { ReadStripActions(read) { openLink(uriHandler::openUri, it) } }
    val date = page.received.shortLabel(layout.today)
    val zen = read.display.zen()
    var stripsHeight by remember { mutableStateOf(0.dp) }
    // Zen hides the strips but keeps their room, so the text does not move and reload.
    val bottomInset = padding.calculateBottomPadding() + PageTokens.pageInset * 2 + stripsHeight
    val headerHeight = remember { mutableStateOf(0.dp) }
    val modules = parts.actions?.behavior()?.readQuickView ?: ReadQuickModule.Defaults
    val expansion = rememberReadExpansion(modules.size, readDetailsHeight(headerHeight.value, padding))
    // A screen reader finds only the grown panel, not the page and strips it covers.
    val covered = expansion.expanded
    Box(Modifier.fillMaxSize()) {
        val swipe = rememberPageSwipe(layout.pager)
        ReadPages(
            state,
            layout.pager,
            layout.look,
            parts.context.loadContent,
            Modifier.fillMaxSize().then(swipe).hiddenWhen(covered),
            PaddingValues(top = headerHeight.value, bottom = bottomInset),
        )

        val pager = layout.pager
        val stripSwipe = rememberPageSwipe(pager, SwipeFeel.Strip)
        val threadSwipe =
            stripSwipe.then(rememberFoldSwipe(rememberUnfoldDrag(layout.folds.thread) { state.threadOpen = it }))
        val slide =
            StripGestures(
                threadSwipe,
                { pager.currentPage + pager.currentPageOffsetFraction },
                rememberFoldSwipe(layout.folds.folders),
            )
        ReadBottomStack(
            state,
            layout.index,
            layout.source,
            strips,
            slide,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(padding)
                .padding(PageTokens.pageInset)
                .holdsSpace(zen)
                .hiddenWhen(covered),
            onFoldedHeight = { stripsHeight = it },
        )
        ReadTop(
            parts,
            ReadTopText(page, date, notice, layout.menu),
            ReadQuickView(expansion, modules, layout.look, layout.pager),
            headerHeight,
        )
    }
}
