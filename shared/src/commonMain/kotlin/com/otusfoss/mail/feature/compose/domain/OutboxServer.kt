package com.otusfoss.mail.feature.compose.domain

import com.otusfoss.mail.core.messages.domain.DraftSender
import com.otusfoss.mail.core.messages.domain.ScheduledSends

/**
 * What the outbox asks the server: [sender] sends a message, [checker] finds out whether a send
 * whose answer was lost went, and [scheduled] cancels or speeds up a send the server holds.
 */
class OutboxServer(
    val sender: DraftSender,
    val checker: SendChecker,
    val scheduled: ScheduledSends,
)
