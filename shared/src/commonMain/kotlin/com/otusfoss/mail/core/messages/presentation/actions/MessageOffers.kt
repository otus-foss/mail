package com.otusfoss.mail.core.messages.presentation.actions

import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_bookmark
import com.otusfoss.core.designsystem.resources.symbol_flag
import com.otusfoss.core.designsystem.resources.symbol_mark_email_read
import com.otusfoss.core.designsystem.resources.symbol_mark_email_unread
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MessageFlag
import com.otusfoss.mail.core.messages.domain.hasFlag
import com.otusfoss.mail.core.messages.presentation.MessageActions
import com.otusfoss.mail.core.messages.presentation.MessageMarks

/** Turns [flag] on for all [messages] while any lacks it, and off otherwise. */
internal fun MessageMarks.offer(
    messages: List<MailMessage>,
    flag: MessageFlag,
): OfferedAction? {
    val markable = messages.filter { can(it, flag) }
    if (markable.isEmpty()) return null
    val on = markable.all { it.hasFlag(flag) }
    val symbol =
        when {
            flag == MessageFlag.Flagged -> Res.drawable.symbol_flag
            on -> Res.drawable.symbol_mark_email_unread
            else -> Res.drawable.symbol_mark_email_read
        }
    return OfferedAction(flag.label(on), symbol, leaves = false) { set(markable, flag, !on) }
}

/** Turns [flag] on for the [messages] that lack it and may take it, as a chain does; null when none does. */
internal fun MessageMarks.offerOn(
    messages: List<MailMessage>,
    flag: MessageFlag,
): OfferedAction? {
    val lacking = messages.filter { can(it, flag) && !it.hasFlag(flag) }
    if (lacking.isEmpty()) return null
    val symbol = if (flag == MessageFlag.Flagged) Res.drawable.symbol_flag else Res.drawable.symbol_mark_email_read
    return OfferedAction(flag.label(on = false), symbol, leaves = false) { set(lacking, flag, true) }
}

/** Saves the messages not saved yet; once all are saved, removes them. */
internal fun MessageActions.offerReadLater(messages: List<MailMessage>): OfferedAction? {
    val changes = messages.mapNotNull(readLater)
    val saving = changes.filter { it.saves }
    val chosen = saving.ifEmpty { changes }.ifEmpty { return null }
    return OfferedAction(chosen.first().label, Res.drawable.symbol_bookmark, chosen.any { it.leaves }) {
        chosen.forEach { it.apply() }
    }
}

internal fun MessageActions.offerRule(messages: List<MailMessage>): OfferedAction? =
    messages.singleOrNull()?.let(ruleForSender)?.let { start ->
        OfferedAction(MailAction.RuleForSender.label, MailAction.RuleForSender.symbol, leaves = true, run = start)
    }
