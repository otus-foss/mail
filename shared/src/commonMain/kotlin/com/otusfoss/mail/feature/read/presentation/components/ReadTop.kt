package com.otusfoss.mail.feature.read.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.otusfoss.core.designsystem.shared.overlays.components.OptionsItem
import com.otusfoss.core.designsystem.shared.panels.components.GROWN_EXPANDED_STOP
import com.otusfoss.core.designsystem.shared.panels.components.PanelExpansion
import com.otusfoss.core.designsystem.shared.panels.components.PanelMotion
import com.otusfoss.core.designsystem.shared.panels.components.PanelScrim
import com.otusfoss.core.designsystem.shared.panels.components.grownPanelHeight
import com.otusfoss.core.designsystem.shared.rows.elements.Caption
import com.otusfoss.core.designsystem.theme.tokens.layout.PageTokens
import com.otusfoss.core.settings.presentation.components.LocalDateFormatter
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.presentation.ReadLaterChange
import com.otusfoss.mail.core.messages.presentation.fullLabel

/**
 * The panel's [expansion], how pages [look], and its measured [height], which sets the expanded
 * stop.
 */
internal class ReadQuickView(
    val expansion: PanelExpansion,
    val look: ReadPageLook,
    val height: MutableState<Dp?>,
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
    strips: ReadStripActions,
    header: MutableState<Dp>,
) {
    val density = LocalDensity.current
    val page = text.page
    val expansion = quick.expansion
    val room = ReadRoom(parts.state, page, expansion, parts, quick.look, strips)
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
            headerActions(parts, expansion, text.menu),
            PanelMotion(expansion = expansion) { ReadExpandedContent(room) { quick.height.value = it } },
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
    expansion: PanelExpansion,
    menu: List<OptionsItem>,
): ReadHeaderActions {
    val actions = parts.actions
    val read = parts.read
    return ReadHeaderActions(
        onBack = parts::close,
        senderOpen = expansion.expanded,
        onDetails = { if (expansion.expanded) expansion.collapse() else expansion.expandTo(GROWN_EXPANDED_STOP) },
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
