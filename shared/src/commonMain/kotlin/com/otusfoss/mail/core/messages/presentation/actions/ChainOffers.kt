package com.otusfoss.mail.core.messages.presentation.actions

import com.otusfoss.mail.core.messages.domain.BarButton
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.MessageFlag

/**
 * [button]'s steps as one action on what this subject holds. A chain only turns things on: it
 * marks unread messages read and unflagged ones flagged, adds labels, and then files the
 * messages. A step nothing here can take is skipped; null when none can.
 */
internal fun ActionSubject.offerChain(button: BarButton.Chain): OfferedAction? {
    val steps = button.steps.mapNotNull(::chainStep)
    if (steps.isEmpty()) return null
    return OfferedAction(button.name, button.symbol, leaves = steps.any { it.leaves }) { steps.forEach { it.run() } }
}

private fun ActionSubject.chainStep(step: BarButton): OfferedAction? =
    when (step) {
        MailAction.Read -> marks?.offerOn(messages, MessageFlag.Read)
        MailAction.Flag -> marks?.offerOn(messages, MessageFlag.Flagged)
        is BarButton.LabelAs -> actions?.offerLabel(messages, step, addOnly = true)
        else -> offer(step)
    }
