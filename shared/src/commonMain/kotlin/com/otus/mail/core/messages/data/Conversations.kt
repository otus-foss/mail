package com.otus.mail.core.messages.data

import com.otus.mail.core.mail.EmailAddressKey
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.isAnswered
import com.otus.mail.core.mail.isFlagged
import com.otus.mail.core.mail.isSeen
import com.otus.mail.core.mail.received
import com.otus.mail.core.mail.receivedTime
import com.otus.mail.core.messages.domain.MailConversation
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.ReceivedAt
import com.otus.mail.core.messages.domain.ReplyState
import com.otus.mail.core.messages.domain.ThreadEmail
import com.otus.mail.core.messages.domain.ThreadMember
import com.otus.mail.core.messages.domain.lastReceived
import kotlinx.datetime.TimeZone

/**
 * This message as the row of its conversation in [mailboxId]. [message] is the mapped [row] email,
 * the newest of the thread in that mailbox, as `Email/query` with `collapseThreads` returns it.
 * [thread] holds the thread's emails; it may lack some, or be empty when the server sent none, and
 * then the row stands for its own email. The read and flag marks and the mailboxes cover the whole
 * conversation, so marking and moving the row act on what it shows.
 */
internal fun MailMessage.inConversation(
    row: EmailObject,
    thread: List<EmailObject>,
    mailboxId: String,
    view: ThreadView,
    zone: TimeZone,
): MailMessage {
    // The row's own copy comes first: it has every row property, a thread member's copy fewer.
    val newestFirst = (listOf(row) + thread).distinctBy { it.id }.sortedByDescending { it.received }
    val listed = listOf(row) + newestFirst.filter { it.id != row.id && mailboxId in it.boxes }
    val listedIds = listed.map { it.id }.toSet()
    val counted = newestFirst.filter { view.counts(it) || it.id in listedIds }
    val last = counted.first()
    val conversation =
        MailConversation(
            threadId = row.threadId.ifEmpty { row.id },
            messageCount = counted.size,
            last =
                ThreadEmail(
                    last.from?.firstOrNull().toSender(location.accountId),
                    last.to.orEmpty().map { it.toSender(location.accountId) },
                    receivedAt(last.receivedTime, zone),
                ),
            senders =
                counted
                    .flatMap { it.from.orEmpty() }
                    .map { it.toSender(location.accountId) }
                    .filter { it.address.isNotBlank() }
                    .distinctBy { EmailAddressKey(it.address) },
            answered = counted.any { it.isAnswered },
            invite = counted.any { it.hasInvite },
            members = listed.map { ThreadMember(it.id, it.boxes, mailboxId) },
            exchanged = counted.any(view::isSent) && counted.any { !view.isSent(it) },
            reply =
                ReplyState(
                    lastFromMe = view.isMine(last),
                    participated = counted.any(view::isMine),
                    lastToMe = view.isToMe(last),
                    lastIsList = last.fromList,
                ),
        )
    return copy(
        conversation = conversation,
        attributes =
            attributes.copy(
                unread = listed.any { !it.isSeen },
                flagged = counted.any { it.isFlagged },
                hasAttachment = counted.any { it.hasFile },
            ),
        location = location.copy(mailboxIds = newestFirst.flatMap { it.boxes }.toSet()),
    )
}

/**
 * One row per thread, newest conversation first. Each mailbox of a list lists its own row of a
 * thread, so rows of one thread merge: the newest row email stays, with every listed member and the
 * marks and mailboxes of all of them. Rows of the same age keep their order, so a message in two
 * mailboxes stays under the first. A row of a folder that lists each email apart stays apart.
 */
internal fun List<MailMessage>.mergedByThread(): List<MailMessage> =
    groupBy { row ->
        row.conversation
            ?.threadId
            ?.takeIf { row.location.mailboxRole?.listsConversations != false } ?: "email:${row.id}"
    }.values
        .map { rows -> rows.reduce(::merge) }
        .sortedByDescending { it.lastReceived.sortKey }

private fun merge(
    first: MailMessage,
    second: MailMessage,
): MailMessage {
    val (kept, other) = if (second.received.sortKey > first.received.sortKey) second to first else first to second
    val conversation = kept.conversation ?: return kept
    val members = (conversation.members + other.conversation?.members.orEmpty()).distinctBy { it.emailId }
    val latest = latest(conversation, other.conversation)
    return kept.copy(
        conversation =
            conversation.copy(
                members = members,
                exchanged = conversation.exchanged || other.conversation?.exchanged == true,
                invite = conversation.invite || other.conversation?.invite == true,
                last = latest.last,
                reply =
                    latest.reply.copy(
                        participated =
                            conversation.reply.participated || other.conversation?.reply?.participated == true,
                    ),
            ),
        attributes =
            kept.attributes.copy(
                unread = kept.attributes.unread || other.attributes.unread,
                flagged = kept.attributes.flagged || other.attributes.flagged,
                hasAttachment = kept.attributes.hasAttachment || other.attributes.hasAttachment,
            ),
        location = kept.location.copy(mailboxIds = kept.location.mailboxIds + other.location.mailboxIds),
    )
}

/**
 * Whichever row saw the newer last email, which gives the merged row its last email and reply
 * facts together. Rows of one thread from different mailboxes count the same emails, unless one
 * mailbox shows mail the other leaves out.
 */
private fun latest(
    kept: MailConversation,
    other: MailConversation?,
): MailConversation = other?.takeIf { it.last.received.sortKey > kept.last.received.sortKey } ?: kept

private val ReceivedAt.sortKey: String
    get() = "${date}T$time"

internal val EmailObject.boxes: Set<String>
    get() = mailboxIds.filterValues { it }.keys
