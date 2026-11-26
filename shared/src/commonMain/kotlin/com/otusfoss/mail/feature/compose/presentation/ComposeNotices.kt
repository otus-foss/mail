package com.otusfoss.mail.feature.compose.presentation

import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.core.messages.domain.DraftFailure

/** What the compose page says under its header when a save fails. */
internal val DraftFailure.notice: String
    get() =
        when (this) {
            DraftFailure.NoDraftsFolder -> "This account has no Drafts folder, so the draft isn't saved."
            is DraftFailure.Mail -> failure.draftNotice
        }

private val MailFailure.draftNotice: String
    get() =
        when (this) {
            MailFailure.NotPermitted -> "The server doesn't allow saving this draft."
            MailFailure.Unreachable -> "Couldn't reach the server. The draft saves with your next change."
            else -> "Couldn't save the draft. It saves again with your next change."
        }

/** What the compose page says while the draft is kept on the device only. */
internal fun waitingDraftNotice(hasUnuploadedAttachments: Boolean): String =
    if (hasUnuploadedAttachments) WAITING_WITHOUT_FILES_NOTICE else WAITING_NOTICE

internal const val WAITING_NOTICE =
    "You're offline. The draft is kept on this device and saves to Drafts when you're back online."

internal const val WAITING_WITHOUT_FILES_NOTICE =
    "You're offline. The draft is kept on this device. Files attached offline aren't saved with it."
