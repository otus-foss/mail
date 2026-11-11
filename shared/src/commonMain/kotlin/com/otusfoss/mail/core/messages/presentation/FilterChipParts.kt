package com.otusfoss.mail.core.messages.presentation

import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_attach_file
import com.otusfoss.core.designsystem.resources.symbol_bookmark
import com.otusfoss.core.designsystem.resources.symbol_event
import com.otusfoss.core.designsystem.resources.symbol_flag
import com.otusfoss.core.designsystem.resources.symbol_forum
import com.otusfoss.core.designsystem.resources.symbol_forward
import com.otusfoss.core.designsystem.resources.symbol_hourglass_empty
import com.otusfoss.core.designsystem.resources.symbol_mark_email_read
import com.otusfoss.core.designsystem.resources.symbol_mark_email_unread
import com.otusfoss.core.designsystem.resources.symbol_newspaper
import com.otusfoss.core.designsystem.resources.symbol_reply
import com.otusfoss.mail.core.messages.domain.DateFilter
import com.otusfoss.mail.core.messages.domain.FilterSort
import com.otusfoss.mail.core.messages.domain.QuickFacet
import com.otusfoss.mail.core.messages.domain.ReadStatus
import com.otusfoss.mail.core.messages.domain.RecipientField
import com.otusfoss.mail.core.messages.domain.SizeFilter
import com.otusfoss.mail.core.messages.domain.TypeFacet
import com.otusfoss.mail.core.settings.presentation.icon
import com.otusfoss.mail.core.settings.presentation.label
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
            SizeFilter.Any -> "Any"
            SizeFilter.Over1Mb -> "1 MB+"
            SizeFilter.Over5Mb -> "5 MB+"
            SizeFilter.Over10Mb -> "10 MB+"
        }

internal val FilterSort.label: String
    get() =
        when (this) {
            FilterSort.NewestFirst -> "Newest first"
            FilterSort.OldestFirst -> "Oldest first"
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
            is DateFilter.Range -> "Custom"
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
