package com.otus.mail.feature.read.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.otus.designsystem.shared.actionbar.components.ActionBarHost
import com.otus.designsystem.shared.overlays.components.LocalSheetPull
import com.otus.designsystem.shared.overlays.components.OptionsItem
import com.otus.designsystem.shared.overlays.components.rememberConfirmation
import com.otus.designsystem.shared.pages.components.belowControlPanel
import com.otus.designsystem.shared.pages.elements.PageSurface
import com.otus.designsystem.shared.pages.elements.hiddenWhen
import com.otus.designsystem.shared.pages.elements.holdsSpace
import com.otus.designsystem.shared.panels.components.GROWN_LAST_STOP
import com.otus.designsystem.theme.motion.MotionCurve
import com.otus.designsystem.theme.motion.MotionSpeed
import com.otus.designsystem.theme.motion.motionTween
import com.otus.designsystem.theme.tokens.layout.PageTokens
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.withContent
import com.otus.mail.core.messages.presentation.MessageActions
import com.otus.mail.core.messages.presentation.components.rememberMoveChoice
import com.otus.mail.core.messages.presentation.shortLabel
import com.otus.mail.feature.read.presentation.ReadContext
import com.otus.mail.feature.read.presentation.ReadState
import com.otus.mail.feature.read.presentation.components.ReadActionBar
import com.otus.mail.feature.read.presentation.components.ReadBottomStack
import com.otus.mail.feature.read.presentation.components.ReadPageLook
import com.otus.mail.feature.read.presentation.components.ReadPages
import com.otus.mail.feature.read.presentation.components.ReadParts
import com.otus.mail.feature.read.presentation.components.ReadQuickView
import com.otus.mail.feature.read.presentation.components.ReadSheets
import com.otus.mail.feature.read.presentation.components.ReadStripActions
import com.otus.mail.feature.read.presentation.components.ReadStripSource
import com.otus.mail.feature.read.presentation.components.ReadThreadOverlay
import com.otus.mail.feature.read.presentation.components.ReadTop
import com.otus.mail.feature.read.presentation.components.ReadTopText
import com.otus.mail.feature.read.presentation.components.StripFolds
import com.otus.mail.feature.read.presentation.components.StripGestures
import com.otus.mail.feature.read.presentation.components.SwipeFeel
import com.otus.mail.feature.read.presentation.components.openLink
import com.otus.mail.feature.read.presentation.components.readDetailsHeight
import com.otus.mail.feature.read.presentation.components.rememberFoldSwipe
import com.otus.mail.feature.read.presentation.components.rememberPageLook
import com.otus.mail.feature.read.presentation.components.rememberPageSwipe
import com.otus.mail.feature.read.presentation.components.rememberReadActions
import com.otus.mail.feature.read.presentation.components.rememberReadExpansion
import com.otus.mail.feature.read.presentation.components.rememberReadMenu
import com.otus.mail.feature.read.presentation.components.rememberStripFolds
import com.otus.mail.feature.read.presentation.components.rememberUnfoldDrag
import com.otus.settings.presentation.components.LocalDateFormatter
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
    read: ReadContext,
    cameFrom: Set<String>,
    onClose: () -> Unit,
) {
    val state = rememberSaveable(message.id, saver = ReadState.saver(message)) { ReadState(message) }
    val pager = rememberPagerState { state.pages.size }
    val edits = read.services.edits
    LaunchedEffect(message.id) {
        val checkpoint = edits?.checkpoint()
        val found = read.loads.pages.messages(message)
        state.showPages(found, checkpoint?.let(edits::since).orEmpty())?.let { pager.scrollToPage(it) }
    }
    LaunchedEffect(message.id) { state.muted = edits?.isMuted(message) }
    DisposableEffect(state, edits) {
        val stop = edits?.observe(state)
        onDispose { stop?.invoke() }
    }
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        onBackCompleted = { if (!state.back()) onClose() },
    )
    val moveChoice = rememberMoveChoice { onClose() }
    val parts = ReadParts(message, actions, read, state, moveChoice, onClose)
    val index = pager.currentPage.coerceIn(0, state.pages.lastIndex)
    val page = state.pages[index].withContent(parts.content(state.pages[index]))
    SideEffect { state.shownId = page.id }
    val look = rememberPageLook(parts)
    val readActions = rememberReadActions(message, page, read.replies, actions, moveChoice)
    val confirmation = rememberConfirmation()
    val menu =
        rememberReadMenu(parts, look, readActions, page, index) { action ->
            confirmation.run(action.confirm) { parts.perform(action) }
        }
    val today = remember { read.display.today() }
    var barPadding by remember { mutableStateOf(PaddingValues()) }
    val scope = rememberCoroutineScope()
    // A tapped message's page arrives like a swiped one, on the design system's easing.
    val turn = motionTween<Float>(MotionSpeed.Normal, MotionCurve.Enter)
    val folds = rememberStripFolds(state)
    val source = ReadStripSource(parts.row, look.folders, cameFrom, today)
    PageSurface {
        ActionBarHost(
            bar = readActions.bar?.let { bar -> { ReadActionBar(bar, page, parts::perform) } },
            // The control panel draws behind the status bar and pads itself.
            Modifier.belowControlPanel(),
        ) { padding ->
            SideEffect { barPadding = padding }
            ReadLayout(parts, ReadLayoutState(pager, index, look, menu, today, folds, source), notice, padding)
        }
        // Over the bar too, which dims with the page while the grown list sits above it.
        if (!read.display.zen()) {
            ReadThreadOverlay(state, index, source, read.replies.own, barPadding, folds.thread) {
                scope.launch { pager.animateScrollToPage(it, animationSpec = turn) }
            }
        }
    }
    // A drag on the folders strip pulls the folders sheet up with the finger.
    CompositionLocalProvider(LocalSheetPull provides folds.folderPull) { ReadSheets(parts, look) }
    confirmation.Dialog()
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
    val date = LocalDateFormatter.current.shortLabel(page.received, layout.today)
    val zen = read.display.zen()
    var stripsHeight by remember { mutableStateOf(0.dp) }
    // Zen hides the strips but keeps their room, so the text does not move and reload.
    val bottomInset = padding.calculateBottomPadding() + PageTokens.pageInset * 2 + stripsHeight
    val headerHeight = remember { mutableStateOf(0.dp) }
    val modules = read.display.quickView()
    val quickHeight = remember { mutableStateOf<Dp?>(null) }
    val expansion =
        rememberReadExpansion(modules.size, quickHeight.value, readDetailsHeight(headerHeight.value, padding))
    // A screen reader finds only the grown panel, not the page and strips it covers.
    val covered = expansion.expanded
    Box(Modifier.fillMaxSize()) {
        val swipe = rememberPageSwipe(layout.pager)
        ReadPages(
            state,
            layout.pager,
            layout.look,
            read.loads.content,
            { expansion.expandTo(GROWN_LAST_STOP) },
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
            ReadQuickView(expansion, modules, layout.look, layout.pager, quickHeight),
            headerHeight,
        )
    }
}
