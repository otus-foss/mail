package com.otus.mail.feature.read.presentation.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.max
import com.otus.designsystem.mail.compose.elements.AddressPill
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_bookmark
import com.otus.designsystem.resources.symbol_bookmark_fill
import com.otus.designsystem.resources.symbol_self_improvement
import com.otus.designsystem.shared.controls.elements.IconPill
import com.otus.designsystem.shared.dates.elements.DatePill
import com.otus.designsystem.shared.panels.components.ChipPlacement
import com.otus.designsystem.shared.panels.components.ContextPanel
import com.otus.designsystem.shared.panels.components.PanelMotion
import com.otus.designsystem.shared.panels.elements.PanelNavigation
import com.otus.designsystem.shared.panels.elements.PanelTitle
import com.otus.designsystem.shared.people.components.PersonAvatar
import com.otus.designsystem.shared.selection.components.Dropdown
import com.otus.designsystem.shared.selection.components.PickMark
import com.otus.designsystem.shared.selection.components.PickRow
import com.otus.designsystem.shared.selection.components.PickStyle
import com.otus.designsystem.theme.motion.motionTween
import com.otus.designsystem.theme.tokens.controls.ControlTokens
import com.otus.designsystem.theme.tokens.core.Sizes
import com.otus.designsystem.theme.tokens.core.Spacing
import com.otus.designsystem.theme.tokens.editing.ComposeTokens
import com.otus.designsystem.theme.tokens.layout.PanelTokens
import com.otus.designsystem.theme.typography.panelTextStyle
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.shownName
import com.otus.mail.core.messages.presentation.ReadLaterChange
import com.otus.mail.core.messages.presentation.components.OptionsButton
import com.otus.mail.core.messages.presentation.components.chipAvatar

/**
 * The Read view's control panel: Back and the [subject] alone, over pills for the [page]'s sender
 * and date, the bookmark, zen mode, and ⋯. Its [motion] grows it into the quick view and details.
 * The date reads [dateLabel] and opens [fullDate]; the sender's name gives way first, so the date
 * never shortens.
 */
@Composable
internal fun ReadHeader(
    subject: String,
    page: MailMessage,
    dateLabel: String,
    fullDate: String,
    actions: ReadHeaderActions,
    motion: PanelMotion = PanelMotion(),
) {
    ContextPanel(
        placement = ChipPlacement.Overlapping,
        navigation = { PanelNavigation("Back", actions.onBack) },
        trailingAction = { OptionsButton(actions.menu, "More actions") },
        chips = {
            BoxWithConstraints(Modifier.fillRail()) {
                val labelMax = senderLabelMax(maxWidth, dateLabel, if (actions.readLater != null) 2 else 1)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.inlineGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item(SENDER) { SenderPill(page, labelMax, actions.senderOpen, actions.onSender) }
                    item(DATE) { DateChip(dateLabel, fullDate) }
                    Spacer(Modifier.weight(1f))
                    actions.readLater?.let { change -> item(BOOKMARK) { Bookmark(change) } }
                    item(ZEN) { ZenPill(actions.zen, actions.onZen) }
                }
            }
        },
        motion = motion,
    ) { ExpandableSubject(subject.ifBlank { "(No subject)" }) }
}

/**
 * The subject beside the chevron, up to [FOLDED_LINES] lines; when it is longer, a tap shows all of
 * it, growing the panel on the layout-change motion, and another tap folds it again.
 */
@Composable
private fun ExpandableSubject(subject: String) {
    var expanded by remember(subject) { mutableStateOf(false) }
    var long by remember(subject) { mutableStateOf(false) }
    PanelTitle(
        subject,
        Modifier
            .padding(start = PanelTokens.titleStart)
            .animateContentSize(motionTween())
            .clickable(
                enabled = long,
                onClickLabel = if (expanded) "Show less" else "Show the whole subject",
            ) { expanded = !expanded },
        maxLines = if (expanded) Int.MAX_VALUE else FOLDED_LINES,
        onTextLayout = { long = if (expanded) it.lineCount > FOLDED_LINES else it.hasVisualOverflow },
    )
}

private const val FOLDED_LINES = 2

@Composable
private fun SenderPill(
    page: MailMessage,
    labelMax: Dp,
    open: Boolean,
    onClick: () -> Unit,
) {
    val name = page.sender.shownName
    val description = if (open) "From $name. Hide details" else "From $name. Show details"
    AddressPill(name, description, onClick, labelMaxWidth = labelMax, expanded = open) {
        PersonAvatar(page.sender.chipAvatar, ComposeTokens.fromAvatarSize)
    }
}

@Composable
private fun DateChip(
    label: String,
    full: String,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        DatePill(label, full, { open = true })
        if (open) {
            Dropdown({ open = false }, width = ComposeTokens.fromLabelMaxWidth) {
                PickRow(full, null, PickMark(false, style = PickStyle.Card), { open = false })
            }
        }
    }
}

/** Zen mode, which hides the banners and strips around the message while it is [on]. */
@Composable
private fun ZenPill(
    on: Boolean,
    onClick: () -> Unit,
) {
    IconPill(if (on) "Leave zen mode" else "Zen mode", Res.drawable.symbol_self_improvement, on, onClick)
}

@Composable
private fun Bookmark(change: ReadLaterChange) {
    IconPill(
        if (change.saves) "Read later" else "Remove from Read later",
        if (change.saves) Res.drawable.symbol_bookmark else Res.drawable.symbol_bookmark_fill,
        selected = !change.saves,
        onClick = change.apply,
    )
}

/**
 * How wide the sender's name may be in a rail of [railWidth]: what is left beside the date pill,
 * the [iconPills] such as the bookmark, the gaps, and the sender pill's own avatar, chevron, and
 * padding.
 */
@Composable
private fun senderLabelMax(
    railWidth: Dp,
    dateLabel: String,
    iconPills: Int,
): Dp {
    val measurer = rememberTextMeasurer()
    val style = panelTextStyle()
    val dateText =
        with(LocalDensity.current) {
            measurer
                .measure(dateLabel, style)
                .size.width
                .toDp()
        }
    val date = max(Sizes.touchTarget, dateText + ControlTokens.chipPadding * 2 + Sizes.borderWidth * 2)
    val pillsWidth = (Sizes.touchTarget + Spacing.inlineGap) * iconPills
    val senderChrome =
        ComposeTokens.fromAvatarSize + Sizes.iconSize + Spacing.iconGap * SENDER_GAPS +
            ControlTokens.chipPadding +
            Sizes.borderWidth * 2
    return (railWidth - date - pillsWidth - Spacing.inlineGap * 2 - senderChrome).coerceAtLeast(
        Sizes.iconSize,
    )
}

/** The gaps in a sender pill: before its avatar, after it, and before the chevron. */
private const val SENDER_GAPS = 3

private const val SENDER = "sender"
private const val DATE = "date"
private const val BOOKMARK = "bookmark"
private const val ZEN = "zen"
