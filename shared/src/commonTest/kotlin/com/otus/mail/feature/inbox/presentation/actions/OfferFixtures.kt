package com.otus.mail.feature.inbox.presentation.actions

import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.MailAttributes
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.MessageFlag
import com.otus.mail.core.messages.domain.ReceivedAt
import com.otus.mail.core.messages.presentation.MessageActions
import com.otus.mail.core.messages.presentation.MessageMarks
import com.otus.mail.core.messages.presentation.MessageMoves
import com.otus.mail.core.messages.presentation.MoveTarget
import com.otus.mail.core.messages.presentation.actions.offer
import com.otus.mail.core.settings.domain.MailBehavior

/** Message actions with no suggestions, rules, or Read later, for testing what bars offer. */
internal fun offerActions(
    targets: (MailMessage) -> List<MoveTarget> = { emptyList() },
    onMove: (MailMessage, MoveTarget) -> Unit = { _, _ -> },
    canMark: (MailMessage, MessageFlag) -> Boolean = { _, _ -> true },
    onMark: (List<MailMessage>, MessageFlag, Boolean) -> Unit = { _, _, _ -> },
) = MessageActions(
    moves = MessageMoves(targets = targets, onMove = onMove, suggested = { null }, futureSorting = { null }),
    marks = MessageMarks(can = canMark, set = onMark),
    ruleForSender = { null },
    readLater = { null },
    behavior = { MailBehavior() },
    cleanup = null,
)

/** A message from Ada in [accountId]'s Inbox, whose email id is "m" and [id]. */
internal fun offerMessage(
    id: String,
    accountId: String = "ada",
    unread: Boolean = false,
) = MailMessage(
    id,
    MailSender("Ada", "ada@example.org"),
    "Subject",
    "Body",
    ReceivedAt("2026-10-01", "12:00"),
    MailLocation("Inbox", accountId, accountId, mailboxId = "inbox", mailboxRole = MailboxRole.Inbox),
    MailAttributes(unread = unread),
    emailId = "m$id",
)
