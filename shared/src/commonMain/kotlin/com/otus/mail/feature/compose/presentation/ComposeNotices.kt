package com.otus.mail.feature.compose.presentation

import com.otus.mail.core.mail.MailFailure
import com.otus.mail.core.messages.domain.DraftFailure

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
