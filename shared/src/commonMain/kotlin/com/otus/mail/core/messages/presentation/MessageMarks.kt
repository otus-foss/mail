package com.otus.mail.core.messages.presentation

import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MessageFlag

/** Read and flag marks: which messages can take one, and turning one on or off. */
class MessageMarks(
    /** Whether the message's folder lets [MessageFlag] change on it. */
    val can: (MailMessage, MessageFlag) -> Boolean,
    /** Turns a mark on or off for some messages together, with Undo; the open message shows it at once. */
    val set: (List<MailMessage>, MessageFlag, Boolean) -> Unit,
    /** Marks a message read as it opens, without the Undo that the user's own marks offer. */
    val opened: (MailMessage) -> Unit = { set(listOf(it), MessageFlag.Read, true) },
)
