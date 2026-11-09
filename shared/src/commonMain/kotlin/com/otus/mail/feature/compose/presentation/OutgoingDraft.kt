package com.otus.mail.feature.compose.presentation

import com.otus.mail.core.messages.domain.AnsweredMessage
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.Draft
import kotlinx.datetime.LocalDateTime

/**
 * A draft the compose page handed over for sending. [ready] gives it as it goes, once any save in
 * progress has ended, so it knows its saved copy. [answered] is the message it replies to or
 * forwards. [sent] tidies up once it went, while the app still runs.
 */
class OutgoingDraft(
    val ready: suspend () -> Draft,
    val answered: AnsweredMessage?,
    val sent: suspend () -> Unit,
)

/**
 * A draft handed over for sending, and [open], which shows [reopen] for Undo. [open] returns once a
 * page has taken the draft, so a message leaves the outbox only then. [scheduledBefore] is the time
 * an edited scheduled mail had.
 */
internal class Outgoing(
    val draft: OutgoingDraft,
    val reopen: ComposeRequest.Resume,
    val scheduledBefore: LocalDateTime? = null,
    val open: suspend (ComposeRequest) -> Unit,
)
