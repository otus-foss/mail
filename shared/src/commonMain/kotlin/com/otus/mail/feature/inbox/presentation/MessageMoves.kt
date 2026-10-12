package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.MailMessage

/** Moving a message: where it can go, the move itself, and what the move sheet suggests. */
class MessageMoves(
    /** Folders the message may move to: they accept mail, and its own folder lets it leave. */
    val targets: (MailMessage) -> List<MoveTarget>,
    /** Moves the message; the list shows the change at once. */
    val onMove: (MailMessage, MoveTarget) -> Unit,
    /** The user's own folder this sender's mail usually goes to; null when none stands out. */
    val suggested: suspend (MailMessage) -> MoveTarget?,
    /** Rules that can also sort future mail like the message; null when its account has no rules. */
    val futureSorting: (MailMessage) -> FutureSorting?,
)

/**
 * Mail like one message that a new rule can sort. [label] completes "Mail that", such as "is from
 * billing@acme.example", and [detail] says who else it catches. [addRule] adds the rule that
 * sends such mail to a folder.
 */
class SortChoice(
    val label: String,
    val detail: String,
    val addRule: (MoveTarget) -> Unit,
)

/** The rules that can sort future mail like a message, saved in [accountName]'s rules. */
class FutureSorting(
    val accountName: String,
    val choices: List<SortChoice>,
)
