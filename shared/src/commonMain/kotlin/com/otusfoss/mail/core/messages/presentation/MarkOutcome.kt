package com.otusfoss.mail.core.messages.presentation

import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MessageFlag
import com.otusfoss.mail.core.messages.domain.hasFlag
import com.otusfoss.mail.core.messages.domain.withFlag

/**
 * What setting [flag] to [on] did: the [messages] as they were before the change, and the ids of
 * those the server [marked].
 */
internal class MarkOutcome(
    val messages: List<MailMessage>,
    val marked: Set<String>,
    val flag: MessageFlag,
    val on: Boolean,
) {
    /** The messages the server refused, as they were, so Retry sends only these. */
    val failed: List<MailMessage> get() = messages.filterNot { it.id in marked }

    /**
     * The messages Undo sets back, as they are after the change. Only those the server changed and
     * that did not already have the mark go back, so marking a mixed selection read and undoing it
     * leaves the messages that were read before still read.
     */
    val changed: List<MailMessage>
        get() = messages.filter { it.id in marked && it.hasFlag(flag) != on }.map { it.withFlag(flag, on) }
}

/** How many of [messages] setting [flag] to [on] changes. */
internal fun changing(
    messages: List<MailMessage>,
    flag: MessageFlag,
    on: Boolean,
): Int = messages.count { it.hasFlag(flag) != on }

/** What the Undo toast says after [count] messages had [flag] set to [on]. */
internal fun markedText(
    flag: MessageFlag,
    on: Boolean,
    count: Int,
): String {
    val mail = if (count == 1) "Email" else "$count emails"
    return "$mail ${markWord(flag, on)}"
}

/**
 * What the Retry toast says when the server refused [failed] of [total] messages whose [flag] was
 * to be set to [on]. A single message is "the email".
 */
internal fun notMarkedText(
    flag: MessageFlag,
    on: Boolean,
    failed: Int,
    total: Int,
): String {
    val mail =
        when {
            total == 1 -> "the email"
            failed == 1 -> "1 email"
            else -> "$failed emails"
        }
    return when (flag) {
        MessageFlag.Read -> "Couldn't mark $mail as ${if (on) "read" else "unread"}"
        MessageFlag.Flagged -> "Couldn't ${if (on) "flag" else "unflag"} $mail"
    }
}

private fun markWord(
    flag: MessageFlag,
    on: Boolean,
): String =
    when (flag) {
        MessageFlag.Read -> if (on) "marked as read" else "marked as unread"
        MessageFlag.Flagged -> if (on) "flagged" else "unflagged"
    }
