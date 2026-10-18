package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.components.PersonAvatar
import com.otus.designsystem.elements.ChipLabel
import com.otus.designsystem.elements.FilterChip
import com.otus.designsystem.elements.FolderGlyph
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_attach_file
import com.otus.designsystem.resources.symbol_bookmark
import com.otus.designsystem.resources.symbol_event
import com.otus.designsystem.resources.symbol_flag
import com.otus.designsystem.resources.symbol_forum
import com.otus.designsystem.resources.symbol_forward
import com.otus.designsystem.resources.symbol_hourglass_empty
import com.otus.designsystem.resources.symbol_mark_email_read
import com.otus.designsystem.resources.symbol_mark_email_unread
import com.otus.designsystem.resources.symbol_newspaper
import com.otus.designsystem.resources.symbol_reply
import com.otus.designsystem.theme.FilterTokens
import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.FolderRef
import com.otus.mail.feature.inbox.domain.QuickFacet
import com.otus.mail.feature.inbox.domain.ReadStatus
import com.otus.mail.feature.inbox.domain.RecipientField
import com.otus.mail.feature.inbox.domain.SizeFilter
import com.otus.mail.feature.inbox.domain.TypeFacet
import com.otus.mail.feature.inbox.presentation.FilterFolder
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.FolderLabel
import com.otus.mail.feature.inbox.presentation.includedCount
import org.jetbrains.compose.resources.DrawableResource

internal val ReadStatus.label: String
    get() =
        when (this) {
            ReadStatus.All -> "All"
            ReadStatus.Unread -> "Unread"
            ReadStatus.Read -> "Read"
        }

internal val ReadStatus.icon: DrawableResource
    get() = if (this == ReadStatus.Read) Res.drawable.symbol_mark_email_read else Res.drawable.symbol_mark_email_unread

internal val QuickFacet.label: String
    get() =
        when (this) {
            QuickFacet.Flagged -> "Flagged"
            QuickFacet.ReadLater -> "Read later"
            QuickFacet.Attachments -> "Attachments"
            QuickFacet.NeedsMyReply -> "Needs my reply"
            QuickFacet.AwaitingReply -> "Awaiting reply"
            QuickFacet.Conversations -> "Conversations"
            QuickFacet.Forwarded -> "Forwarded"
        }

internal val QuickFacet.icon: DrawableResource
    get() =
        when (this) {
            QuickFacet.Flagged -> Res.drawable.symbol_flag
            QuickFacet.ReadLater -> Res.drawable.symbol_bookmark
            QuickFacet.Attachments -> Res.drawable.symbol_attach_file
            QuickFacet.NeedsMyReply -> Res.drawable.symbol_reply
            QuickFacet.AwaitingReply -> Res.drawable.symbol_hourglass_empty
            QuickFacet.Conversations -> Res.drawable.symbol_forum
            QuickFacet.Forwarded -> Res.drawable.symbol_forward
        }

internal val TypeFacet.label: String
    get() =
        when (this) {
            TypeFacet.Newsletters -> "Newsletters"
            TypeFacet.CalendarInvites -> "Calendar invites"
        }

internal val TypeFacet.icon: DrawableResource
    get() =
        when (this) {
            TypeFacet.Newsletters -> Res.drawable.symbol_newspaper
            TypeFacet.CalendarInvites -> Res.drawable.symbol_event
        }

internal val RecipientField.label: String
    get() =
        when (this) {
            RecipientField.To -> "To"
            RecipientField.Cc -> "Cc"
            RecipientField.Bcc -> "Bcc"
        }

internal val SizeFilter.label: String
    get() =
        when (this) {
            SizeFilter.Any -> "Any size"
            SizeFilter.Over1Mb -> "Over 1 MB"
            SizeFilter.Over5Mb -> "Over 5 MB"
            SizeFilter.Over10Mb -> "Over 10 MB"
        }

/** A preset's name; a range has its own label, from [rangeLabel]. */
internal val DateFilter.label: String
    get() =
        when (this) {
            DateFilter.Any -> "Any time"
            DateFilter.Today -> "Today"
            DateFilter.Last7 -> "Last 7 days"
            DateFilter.Last30 -> "Last 30 days"
            DateFilter.ThisYear -> "This year"
            is DateFilter.Range -> "Custom…"
        }

/** A custom range in the user's date format, such as "12.10.2026 – 18.10.2026". */
internal fun rangeLabel(
    range: DateFilter.Range,
    dateFormatter: DateFormatter,
): String =
    when (range.from) {
        range.to -> dateFormatter.format(range.from)
        else -> dateFormatter.formatRange(range.from, range.to, " – ")
    }

/** A folder's name, with " +N" for the subfolders a chosen [ref] includes. */
internal fun FolderLabel.withIncluded(
    ref: FolderRef?,
    options: FilterOptions,
): String {
    val included = ref?.let(options::includedCount) ?: 0
    return if (included > 0) "$name +$included" else name
}

/**
 * A folder shortcut: its glyph, after the account's avatar when another account has a folder
 * at the same path, and its label. [chosen] is its selection, null while off.
 */
@Composable
internal fun FolderChip(
    folder: FilterFolder,
    chosen: FolderRef?,
    options: FilterOptions,
    onClick: () -> Unit,
) {
    val label = options.labels[folder.key] ?: FolderLabel(folder.name)
    val account = options.account(folder.key.loginId).takeIf { label.showAccount }
    FilterChip(
        ChipLabel(label.withIncluded(chosen, options), label.prefix, label.spoken),
        selected = chosen != null,
        onClick = onClick,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(FilterTokens.chipIconGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            account?.let { PersonAvatar(it.avatar, FilterTokens.chipIconSize) }
            FolderGlyph(folder.color, icon = folder.icon, size = FilterTokens.chipIconSize)
        }
    }
}
