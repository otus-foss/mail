package com.otus.mail.feature.read.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.otus.designsystem.shared.overlays.components.OptionsItem
import com.otus.designsystem.shared.panels.components.PanelExpansion
import com.otus.designsystem.shared.panels.components.PanelMotion
import com.otus.designsystem.shared.panels.components.PanelScrim
import com.otus.designsystem.shared.panels.components.grownPanelHeight
import com.otus.designsystem.shared.rows.elements.Caption
import com.otus.designsystem.theme.motion.MotionCurve
import com.otus.designsystem.theme.motion.MotionSpeed
import com.otus.designsystem.theme.motion.motionTween
import com.otus.designsystem.theme.tokens.layout.PageTokens
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.presentation.ReadLaterChange
import com.otus.mail.core.messages.presentation.fullLabel
import com.otus.mail.core.settings.domain.ReadQuickModule
import com.otus.settings.presentation.components.LocalDateFormatter
import kotlinx.coroutines.launch

/**
 * The panel's [expansion], the quick view [modules] it shows in the user's order, how pages
 * [look], and the [pager] its modules turn.
 */
internal class ReadQuickView(
    val expansion: PanelExpansion,
    val modules: List<ReadQuickModule>,
    val look: ReadPageLook,
    val pager: PagerState,
)

/**
 * The [page] on screen, its short [date], the [notice] under the header, if any, and the ⋯ [menu].
 */
internal class ReadTopText(
    val page: MailMessage,
    val date: String,
    val notice: String?,
    val menu: List<OptionsItem>,
)

/**
 * The control panel over the page on screen, which grows into the quick view and every detail
 * over a dim. Its resting height goes to [header], which the pages keep clear of.
 */
@Composable
internal fun ReadTop(
    parts: ReadParts,
    text: ReadTopText,
    quick: ReadQuickView,
    header: MutableState<Dp>,
) {
    val density = LocalDensity.current
    val page = text.page
    val expansion = quick.expansion
    val scope = rememberCoroutineScope()
    val turn = motionTween<Float>(MotionSpeed.Normal, MotionCurve.Enter)
    val room =
        ReadRoom(parts.state, page, parts.read.replies.own, expansion, parts, quick.look) { index ->
            scope.launch { quick.pager.animateScrollToPage(index, animationSpec = turn) }
        }
    PanelScrim(expansion)
    Column(
        Modifier.onSizeChanged { size ->
            val height = with(density) { size.height.toDp() }
            // The room stays the tallest header, so folding the subject does not move the text; a
            // panel pulled open grows over the page instead.
            if (height > header.value && !expansion.expanded) header.value = height
        },
    ) {
        ReadHeader(
            parts.row.subject,
            page,
            text.date,
            LocalDateFormatter.current.fullLabel(page.received),
            headerActions(parts, text.menu),
            PanelMotion(expansion = expansion) { ReadExpandedContent(room, quick.modules) },
        )
        text.notice?.let {
            Box(Modifier.padding(PageTokens.pageInset)) {
                Caption(it, Modifier.padding(horizontal = PageTokens.textInset), heading = false)
            }
        }
    }
}

/**
 * How tall the Read view's panel grows for every detail: the window below the header, less the
 * bar's [padding] and a gap.
 */
@Composable
internal fun readDetailsHeight(
    header: Dp,
    padding: PaddingValues,
): Dp = grownPanelHeight(above = header, below = padding.calculateBottomPadding() + PageTokens.pageInset)

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
        zen = read.display.zen(),
        onZen = {
            val on = !read.display.zen()
            // Zen hides the strips, so their message list and files fold first.
            if (on) {
                parts.state.threadOpen = false
                parts.state.filesOpenOn = null
            }
            read.display.setZen(on)
        },
        // A change that takes the message off this list closes the page first, as filing does.
        readLater =
            actions?.readLater(parts.row)?.let { change ->
                if (change.leaves) {
                    ReadLaterChange(change.saves, leaves = true) {
                        parts.close()
                        change.apply()
                    }
                } else {
                    change
                }
            },
        menu = menu,
    )
}
