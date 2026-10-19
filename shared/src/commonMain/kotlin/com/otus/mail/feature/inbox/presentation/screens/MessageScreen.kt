package com.otus.mail.feature.inbox.presentation.screens

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.otus.designsystem.components.ActionBarHost
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
import com.otus.mail.feature.inbox.presentation.components.StripSlide
import com.otus.mail.feature.inbox.presentation.components.openLink
import com.otus.mail.feature.inbox.presentation.components.rememberMoveChoice
import com.otus.mail.feature.inbox.presentation.components.rememberPageLook
import com.otus.mail.feature.inbox.presentation.components.rememberPageSwipe
import com.otus.mail.feature.inbox.presentation.components.rememberReadBar
import com.otus.mail.feature.inbox.presentation.components.rememberReadMenu
import com.otus.mail.feature.inbox.presentation.read.fullLabel
import com.otus.mail.feature.inbox.presentation.read.shortLabel
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * An open message or conversation as a page over the list. Each message of the conversation is a
 * page, newest first. Filing acts on the whole conversation and closes the page first, while
 * replies and the ⋯ menu's message entries follow the page on screen. A [notice], such as a
 * refused mark, shows under the header. [cameFrom] is the folder the list shows, which the strips
 * leave out; null without a folder context. Back folds the message list, then the files, then the
 * details, then runs [onClose]. The details and the page on screen survive a recreated screen.
 */
@Composable
fun MessageScreen(
    message: MailMessage,
    actions: MessageActions?,
    notice: String?,
    context: InboxContext,
    cameFrom: String?,
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
    val today = remember { read.today() }
    var barPadding by remember { mutableStateOf(PaddingValues()) }
    val scope = rememberCoroutineScope()
    Surface(Modifier.fillMaxSize(), color = OtusColors.bgRegular) {
        ActionBarHost(
            bar = bar.layout?.let { { ReadActionBar(bar, page, parts::perform) } },
            Modifier.safeDrawingPadding(),
        ) { padding ->
            SideEffect { barPadding = padding }
            ReadLayout(parts, ReadLayoutState(pager, index, look, menu, today), notice, cameFrom, padding)
        }
        // Over the bar too, which dims with the page while the grown list sits above it.
        ReadThreadOverlay(state, index, today, context.rowLook.own, barPadding) {
            scope.launch { pager.animateScrollToPage(it) }
        }
    }
    ReadSheets(parts, look, cameFrom)
    actions?.let { moveChoice.Sheet(it) }
}

/**
 * Where the Read view's layout stands: the [pager] on the page at [index], how pages [look], the ⋯
 * [menu], and the [today] dates are shown against.
 */
private class ReadLayoutState(
    val pager: PagerState,
    val index: Int,
    val look: ReadPageLook,
    val menu: List<OptionsItem>,
    val today: LocalDate,
)

/** The header, the pages, and the strips above the bar's [padding]. */
@Composable
private fun ReadLayout(
    parts: ReadParts,
    layout: ReadLayoutState,
    notice: String?,
    cameFrom: String?,
    padding: PaddingValues,
) {
    val state = parts.state
    val read = parts.read
    val row = parts.row
    val page = state.pages[layout.index]
    val uriHandler = LocalUriHandler.current
    val strips = remember(read) { ReadStripActions(read) { openLink(uriHandler::openUri, it) } }
    val date = page.received.shortLabel(layout.today)
    Column(Modifier.fillMaxSize().padding(padding).padding(PageTokens.pageInset)) {
        ReadHeader(row.subject, page, date, page.received.fullLabel(), headerActions(parts, layout.menu))
        notice?.let { MessageNotice(it) }
        val swipe = rememberPageSwipe(layout.pager)
        ReadPages(state, layout.pager, layout.look, parts.context.loadContent, Modifier.weight(1f).then(swipe))
        val pager = layout.pager
        val slide = StripSlide(swipe) { pager.currentPage + pager.currentPageOffsetFraction }
        ReadBottomStack(
            state,
            layout.index,
            ReadStripSource(row, layout.look.folders, cameFrom, layout.today),
            strips,
            slide,
        )
    }
}

private fun headerActions(
    parts: ReadParts,
    menu: List<OptionsItem>,
): ReadHeaderActions {
    val actions = parts.actions
    return ReadHeaderActions(
        onBack = parts::close,
        senderOpen = parts.state.details,
        onSender = { parts.state.details = !parts.state.details },
        // A change that takes the message off this list closes the page first, as filing does.
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
