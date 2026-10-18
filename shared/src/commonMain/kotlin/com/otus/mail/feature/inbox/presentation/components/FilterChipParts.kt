package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.components.PersonAvatar
import com.otus.designsystem.elements.FilterChip
import com.otus.designsystem.elements.FolderGlyph
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_attach_file
import com.otus.designsystem.resources.symbol_bookmark
import com.otus.designsystem.resources.symbol_event
import com.otus.designsystem.resources.symbol_flag
import com.otus.designsystem.resources.symbol_forward
import com.otus.designsystem.resources.symbol_mark_email_read
import com.otus.designsystem.resources.symbol_mark_email_unread
import com.otus.designsystem.resources.symbol_newspaper
import com.otus.designsystem.resources.symbol_reply
import com.otus.designsystem.resources.symbol_speaker_notes_off
import com.otus.designsystem.theme.FilterTokens
import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.FolderRef
import com.otus.mail.feature.inbox.domain.RecipientField
import com.otus.mail.feature.inbox.domain.ShowFacet
import com.otus.mail.feature.inbox.domain.SizeFilter
import com.otus.mail.feature.inbox.presentation.FilterFolder
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.FolderLabel
import com.otus.mail.feature.inbox.presentation.includedCount
import org.jetbrains.compose.resources.DrawableResource

internal val ShowFacet.label: String
    get() =
        when (this) {
            ShowFacet.Unread -> "Unread"
            ShowFacet.Read -> "Read"
            ShowFacet.Flagged -> "Flagged"
            ShowFacet.NotFlagged -> "Not flagged"
            ShowFacet.ReadLater -> "Read later"
            ShowFacet.Attachments -> "Attachments"
            ShowFacet.Replied -> "Replied"
            ShowFacet.NotReplied -> "Not replied"
            ShowFacet.Forwarded -> "Forwarded"
            ShowFacet.WaitingForReply -> "Waiting for reply"
            ShowFacet.Newsletters -> "Newsletters"
            ShowFacet.CalendarInvites -> "Calendar invites"
        }

internal val ShowFacet.icon: DrawableResource
    get() =
        when (this) {
            ShowFacet.Unread -> Res.drawable.symbol_mark_email_unread
            ShowFacet.Read -> Res.drawable.symbol_mark_email_read
            ShowFacet.Flagged, ShowFacet.NotFlagged -> Res.drawable.symbol_flag
            ShowFacet.ReadLater -> Res.drawable.symbol_bookmark
            ShowFacet.Attachments -> Res.drawable.symbol_attach_file
            ShowFacet.Replied, ShowFacet.WaitingForReply -> Res.drawable.symbol_reply
            ShowFacet.NotReplied -> Res.drawable.symbol_speaker_notes_off
            ShowFacet.Forwarded -> Res.drawable.symbol_forward
            ShowFacet.Newsletters -> Res.drawable.symbol_newspaper
            ShowFacet.CalendarInvites -> Res.drawable.symbol_event
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
        label.withIncluded(chosen, options),
        selected = chosen != null,
        onClick = onClick,
        prefix = label.prefix,
        description = label.spoken,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(FilterTokens.chipIconGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            account?.let { PersonAvatar(it.avatar, FilterTokens.chipIconSize) }
            FolderGlyph(folder.color, folder.icon)
        }
    }
}
