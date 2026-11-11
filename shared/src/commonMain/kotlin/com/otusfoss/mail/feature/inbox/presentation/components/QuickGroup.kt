package com.otusfoss.mail.feature.inbox.presentation.components

import com.otusfoss.mail.core.messages.domain.QuickFacet
import com.otusfoss.mail.core.messages.presentation.label
import com.otusfoss.mail.core.settings.presentation.label

/** A captioned group of Quick filters on the filter panel's main page, in page order. */
internal enum class QuickGroup(
    val label: String,
) {
    Saved("Saved"),
    Replies("Replies"),
    Mail("Mail"),
}

internal val QuickFacet.group: QuickGroup
    get() =
        when (this) {
            QuickFacet.Flagged, QuickFacet.ReadLater -> QuickGroup.Saved
            QuickFacet.NeedsMyReply, QuickFacet.AwaitingReply -> QuickGroup.Replies
            QuickFacet.Attachments, QuickFacet.Conversations, QuickFacet.Forwarded -> QuickGroup.Mail
        }
