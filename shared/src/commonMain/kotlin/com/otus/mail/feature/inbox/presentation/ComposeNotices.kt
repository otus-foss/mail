package com.otus.mail.feature.inbox.presentation

import com.otus.mail.core.messages.domain.DraftFailure

/** What the compose page says under its header when a save fails. */
internal val DraftFailure.notice: String
    get() =
        when (this) {
            DraftFailure.NoDraftsFolder -> "This account has no Drafts folder, so the draft isn't saved."
            DraftFailure.NotPermitted -> "The server doesn't allow saving this draft."
            DraftFailure.Unreachable -> "Couldn't reach the server. The draft saves with your next change."
            DraftFailure.ServerProblem -> "Couldn't save the draft. It saves again with your next change."
        }
