package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.otus.designsystem.components.ChipPlacement
import com.otus.designsystem.components.ContextPanel
import com.otus.designsystem.components.Dropdown
import com.otus.designsystem.components.DropdownRow
import com.otus.designsystem.components.OptionsItem
import com.otus.designsystem.components.PersonAvatar
import com.otus.designsystem.elements.AddressPill
import com.otus.designsystem.elements.DatePill
import com.otus.designsystem.elements.IconButton
import com.otus.designsystem.elements.IconPill
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_bookmark
import com.otus.designsystem.resources.symbol_bookmark_fill
import com.otus.designsystem.resources.symbol_chevron_left
import com.otus.designsystem.theme.ComposeTokens
import com.otus.designsystem.theme.PanelTokens
import com.otus.designsystem.theme.panelTextStyle
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.ReadLaterChange

/**
 * The Read view's control panel: Back and the [subject] alone, over pills for the [page]'s sender
 * and date, the bookmark, and ⋯. The date reads [dateLabel] and opens [fullDate]; the sender's name
 * gives way first, so the date never shortens.
 */
@Composable
internal fun ReadHeader(
    subject: String,
    page: MailMessage,
    dateLabel: String,
    fullDate: String,
    actions: ReadHeaderActions,
) {
    ContextPanel(
        placement = ChipPlacement.Overlapping,
        navigation = {
            IconButton("Back", actions.onBack, outlined = false) {
                Symbol(Res.drawable.symbol_chevron_left, mirrorInRtl = true, size = PanelTokens.navigationIconSize)
            }
        },
        trailingAction = { OptionsButton(actions.menu, "More actions") },
        chips = {
            BoxWithConstraints(Modifier.fillRail()) {
                val labelMax = senderLabelMax(maxWidth, dateLabel, actions.readLater != null)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PanelTokens.chipGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item(SENDER) { SenderPill(page, labelMax, actions.senderOpen, actions.onSender) }
                    item(DATE) { DateChip(dateLabel, fullDate) }
                    Spacer(Modifier.weight(1f))
                    actions.readLater?.let { change -> item(BOOKMARK) { Bookmark(change) } }
                }
            }
        },
    ) {
        Text(
            subject.ifBlank { "(No subject)" },
            Modifier.semantics { heading() },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SenderPill(
    page: MailMessage,
    labelMax: Dp,
    open: Boolean,
    onClick: () -> Unit,
) {
    val name = page.sender.name.ifBlank { page.sender.address }
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
                DropdownRow(full, selected = false, onClick = { open = false })
            }
        }
    }
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
 * the bookmark, the gaps, and the sender pill's own avatar, chevron, and padding.
 */
@Composable
private fun senderLabelMax(
    railWidth: Dp,
    dateLabel: String,
    bookmark: Boolean,
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
    val date = max(PanelTokens.touchTarget, dateText + PanelTokens.chipPadding * 2 + PanelTokens.borderWidth * 2)
    val bookmarkWidth = if (bookmark) PanelTokens.touchTarget + PanelTokens.chipGap else 0.dp
    val senderChrome =
        ComposeTokens.fromAvatarSize + PanelTokens.iconSize + PanelTokens.iconGap * SENDER_GAPS +
            PanelTokens.chipPadding +
            PanelTokens.borderWidth * 2
    return (railWidth - date - bookmarkWidth - PanelTokens.chipGap * 2 - senderChrome).coerceAtLeast(
        PanelTokens.iconSize,
    )
}

/** The gaps in a sender pill: before its avatar, after it, and before the chevron. */
private const val SENDER_GAPS = 3

private const val SENDER = "sender"
private const val DATE = "date"
private const val BOOKMARK = "bookmark"
