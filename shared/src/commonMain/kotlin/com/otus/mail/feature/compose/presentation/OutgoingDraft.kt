package com.otus.mail.feature.compose.presentation

import com.otus.mail.core.messages.domain.AnsweredMessage
import com.otus.mail.core.messages.domain.Draft

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
