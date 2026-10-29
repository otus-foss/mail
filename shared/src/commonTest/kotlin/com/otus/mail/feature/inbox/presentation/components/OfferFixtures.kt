package com.otus.mail.feature.inbox.presentation.components

import com.otus.mail.feature.inbox.domain.MailAttributes
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.MessageMoves
import com.otus.mail.feature.inbox.presentation.MoveTarget

/** Message actions with no suggestions, rules, or Read later, for testing what bars offer. */
internal fun offerActions(
    targets: (MailMessage) -> List<MoveTarget> = { emptyList() },
    onMove: (MailMessage, MoveTarget) -> Unit = { _, _ -> },
    canMark: (MailMessage, MessageFlag) -> Boolean = { _, _ -> true },
    onMark: (List<MailMessage>, MessageFlag, Boolean) -> Unit = { _, _, _ -> },
) = MessageActions(
    moves = MessageMoves(targets = targets, onMove = onMove, suggested = { null }, futureSorting = { null }),
    canMark = canMark,
    onMark = onMark,
    ruleForSender = { null },
    readLater = { null },
    behavior = { MailBehavior() },
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
    MailLocation("Inbox", accountId, accountId, mailboxId = "inbox"),
    MailAttributes(unread = unread),
    emailId = "m$id",
)
