package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender

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
 * billing@acme.example". In the choices it reads as [keyword] before [value], such as "From" before
 * "billing@acme.example", over [detail], which says who else it catches. [person] is the sender,
 * recipient, or company [value] names; null for a subject word.
 * [addRule] adds the rule that sends such mail to the folder the messages moved to.
 */
class SortChoice(
    val label: String,
    val keyword: String,
    val value: String,
    val detail: String,
    val person: MailSender?,
    val addRule: (MoveTarget, moved: List<MailMessage>) -> Unit,
)

/** The rules that can sort future mail like a message, saved in [accountName]'s rules. */
class FutureSorting(
    val accountName: String,
    val choices: List<SortChoice>,
)

/**
 * A move that also added a rule, offered back once the rule is saved. [catches] names the mail the
 * rule sorts, such as "from billing@acme.example". [undo] puts the messages back and deletes the
 * rule. [older] looks for older inbox mail the rule matches; null when the server cannot be read.
 * [person] is the one [catches] names, as in [SortChoice].
 */
class SortedMove(
    val folderName: String,
    val catches: String,
    val person: MailSender?,
    val undo: () -> Unit,
    val older: suspend () -> OlderMail?,
)

/** [count] older inbox messages a new rule matches, which [move] files like new mail. */
class OlderMail(
    val count: Int,
    val move: () -> Unit,
)
