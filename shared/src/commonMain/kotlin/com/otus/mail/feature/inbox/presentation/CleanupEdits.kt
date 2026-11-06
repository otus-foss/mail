package com.otus.mail.feature.inbox.presentation

import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.feature.inbox.domain.MailMessage
import kotlinx.coroutines.launch

/**
 * Moves [message] out of Spam into [inbox]; [keywords] also clears the spam mark. It leaves [feed]
 * at once and comes back, with a notice, if the server refuses.
 */
internal fun MailEdits.notSpam(
    feed: InboxFeedState,
    message: MailMessage,
    inbox: MoveTarget,
    keywords: Boolean,
) {
    val edit = show(leaveEdit(feed, message))
    scope.launch {
        val kept = writer.notSpam(message, inbox.folderId, keywords)
        settle(edit, kept)
        if (!kept) {
            feed.refresh()
            feed.report("Couldn't move the message to ${inbox.label}. Try again.")
        }
    }
}

/** Deletes [messages] for good; those the server keeps come back, with a notice. */
internal fun MailEdits.delete(
    feed: InboxFeedState,
    messages: List<MailMessage>,
) {
    val shown = messages.associateWith { show(leaveEdit(feed, it)) }
    scope.launch {
        val deleted = writer.delete(messages)
        settle(shown.entries.associate { (message, edit) -> edit to (message.id in deleted) })
        val kept = messages.size - deleted.size
        if (kept > 0) {
            feed.refresh()
            feed.report("Couldn't finish deleting ${countText(kept)}. Try again.")
        }
    }
}

/** Takes the loaded messages of [emptied] off [feed], and puts back those the server kept. */
internal fun MailEdits.empty(
    feed: InboxFeedState,
    emptied: List<MailFolder>,
) {
    val shown =
        feed.messages
            .filter { message ->
                emptied.any { it.accountId == message.location.accountId && it.id == message.location.mailboxId }
            }.associateWith { show(leaveEdit(feed, it)) }
    scope.launch {
        val results = emptied.map { it.accountId to writer.empty(it.accountId, it.id) }
        val removed = results.flatMap { (account, deletion) -> deletion.emailIds.map { account to it } }.toSet()
        val outcomes =
            shown.entries.associate { (message, edit) ->
                val members = message.conversation?.members?.map { it.emailId } ?: listOf(message.emailId)
                edit to (members.isNotEmpty() && members.all { message.location.accountId to it in removed })
            }
        settle(outcomes)
        // Mail that was never loaded left too.
        announce()
        val incomplete = results.any { !it.second.complete }
        // A complete empty can still keep a row whose members moved away meanwhile; refresh shows where they went.
        if (incomplete || !outcomes.values.all { it }) feed.refresh()
        if (incomplete) feed.report("Couldn't delete every message. Try again.")
    }
}

internal fun countText(count: Int): String = if (count == 1) "1 message" else "$count messages"
