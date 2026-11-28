package com.otusfoss.mail.feature.read.presentation.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.otusfoss.core.designsystem.mail.attachments.components.FilesPill
import com.otusfoss.core.designsystem.mail.compose.elements.AddressPill
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_bookmark
import com.otusfoss.core.designsystem.resources.symbol_bookmark_fill
import com.otusfoss.core.designsystem.shared.controls.elements.IconPill
import com.otusfoss.core.designsystem.shared.dates.elements.DatePill
import com.otusfoss.core.designsystem.shared.panels.components.ChipPlacement
import com.otusfoss.core.designsystem.shared.panels.components.ContextPanel
import com.otusfoss.core.designsystem.shared.panels.components.PanelMotion
import com.otusfoss.core.designsystem.shared.panels.elements.PanelNavigation
import com.otusfoss.core.designsystem.shared.panels.elements.PanelTitle
import com.otusfoss.core.designsystem.shared.people.components.PersonAvatar
import com.otusfoss.core.designsystem.theme.colors.OtusColors
import com.otusfoss.core.designsystem.theme.motion.motionTween
import com.otusfoss.core.designsystem.theme.tokens.core.Spacing
import com.otusfoss.core.designsystem.theme.tokens.layout.PanelTokens
import com.otusfoss.core.designsystem.theme.tokens.people.SenderTokens
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.shownName
import com.otusfoss.mail.core.messages.presentation.ReadLaterChange
import com.otusfoss.mail.core.messages.presentation.components.OptionsButton
import com.otusfoss.mail.core.messages.presentation.components.chipAvatar

/**
 * The Read view's control panel: Back and the [subject] alone, over pills for the [page]'s sender
 * and date, the bookmark, the page's files, and ⋯. Its [motion] grows it into the quick view and details.
 * The date reads [dateLabel], and screen readers read [fullDate]; like the sender pill, it shows or
 * hides the details, which hold the full date. The sender's pill takes whatever room is left, so
 * the date never shortens and no gap opens beside a cut-off name.
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
            Row(
                Modifier.fillRail(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.inlineGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.inlineGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item(SENDER) {
                        SenderPill(page, actions.senderOpen, actions.onDetails, Modifier.weight(1f, fill = false))
                    }
                    item(DATE) { DatePill(dateLabel, fullDate, actions.onDetails) }
                }
                actions.readLater?.let { change -> item(BOOKMARK) { Bookmark(change) } }
                actions.files.takeIf { it.isNotEmpty() }?.let { item(FILES) { FilesPill(it, actions.onFiles) } }
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

/** The sender's pill, which takes the room the date and the pills after it leave, its name fading where it does not fit. */
@Composable
private fun SenderPill(
    page: MailMessage,
    open: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val name = page.sender.shownName
    val description = if (open) "From $name. Hide details" else "From $name. Show details"
    AddressPill(name, description, onClick, modifier, labelMaxWidth = Dp.Infinity, expanded = open) {
        PersonAvatar(page.sender.chipAvatar, SenderTokens.avatarSize)
    }
}

@Composable
private fun Bookmark(change: ReadLaterChange) {
    IconPill(
        if (change.saves) "Read later" else "Remove from Read later",
        if (change.saves) Res.drawable.symbol_bookmark else Res.drawable.symbol_bookmark_fill,
        selected = !change.saves,
        onClick = change.apply,
        fill = OtusColors.bgDark,
    )
}

private const val SENDER = "sender"
private const val DATE = "date"
private const val BOOKMARK = "bookmark"
private const val FILES = "files"
