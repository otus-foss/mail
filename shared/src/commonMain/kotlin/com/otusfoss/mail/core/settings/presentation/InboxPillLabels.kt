package com.otusfoss.mail.core.settings.presentation

import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_attach_file
import com.otusfoss.core.designsystem.resources.symbol_flag
import com.otusfoss.core.designsystem.resources.symbol_forum
import com.otusfoss.core.designsystem.resources.symbol_mark_email_unread
import com.otusfoss.mail.core.settings.domain.InboxPill
import org.jetbrains.compose.resources.DrawableResource

/** The filter as its pill and the settings list name it. */
internal val InboxPill.Filter.label: String
    get() =
        when (this) {
            InboxPill.Filter.Unread -> "Unread"
            InboxPill.Filter.Attachments -> "Attachments"
            InboxPill.Filter.Flagged -> "Flagged"
            InboxPill.Filter.Conversations -> "Conversations"
        }

internal val InboxPill.Filter.icon: DrawableResource
    get() =
        when (this) {
            InboxPill.Filter.Unread -> Res.drawable.symbol_mark_email_unread
            InboxPill.Filter.Attachments -> Res.drawable.symbol_attach_file
            InboxPill.Filter.Flagged -> Res.drawable.symbol_flag
            InboxPill.Filter.Conversations -> Res.drawable.symbol_forum
        }
