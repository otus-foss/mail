package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.DraftFailure
import com.otus.mail.feature.inbox.domain.SendFailure

/** What the compose page says under its header when a save or a send fails. */
internal val DraftFailure.notice: String
    get() =
        when (this) {
            DraftFailure.NoDraftsFolder -> "This account has no Drafts folder, so the draft isn't saved."
            DraftFailure.NotPermitted -> "The server doesn't allow saving this draft."
            DraftFailure.Unreachable -> "Couldn't reach the server. The draft saves with your next change."
            DraftFailure.ServerProblem -> "Couldn't save the draft. It saves again with your next change."
        }

internal val SendFailure.notice: String
    get() =
        when (this) {
            SendFailure.NoIdentity -> "The server doesn't let this account send from this address."
            SendFailure.Recipients -> "Check the recipients. The server can't send to one of them."
            SendFailure.TooManyRecipients -> "Too many recipients for this server. Remove some and try again."
            SendFailure.TooLarge -> "The message is too large for this server to send."
            SendFailure.NotPermitted -> "The server doesn't allow sending this message."
            SendFailure.Unreachable -> "Lost the connection while sending. Check Sent before you try again."
            SendFailure.ServerProblem -> "Couldn't send the message. Try again."
        }
