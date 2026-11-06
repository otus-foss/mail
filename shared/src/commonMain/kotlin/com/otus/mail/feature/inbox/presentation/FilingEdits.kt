package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.inFolder
import com.otus.mail.feature.inbox.domain.memberMailboxes
import kotlinx.coroutines.Job

/** [message] off the list [feed] shows, until the server refuses the change. */
internal fun leaveEdit(
    feed: InboxFeedState,
    message: MailMessage,
): MailEdit = MailEdit(message.id, message.memberMailboxes.keys, leaves = feed.mailScope)

/**
 * Moves [message] out of [leaving] into [target]: it leaves [feed] at once and comes back if the
 * server refuses. A successful move does not reload the list; [feed] keeps the message out of
 * loads that may predate the move. The returned job ends when the server has answered.
 */
internal fun MailEdits.move(
    feed: InboxFeedState,
    message: MailMessage,
    target: MoveTarget,
    leaving: Set<String>,
): Job =
    save(leaveEdit(feed, message), feed, "Couldn't move the message to ${target.label}. Try again.") {
        writer.move(message, target.folderId, leaving)
    }

/** [move] with the Undo toast, whose Undo waits for the move so the two cannot cross. */
internal fun MailEdits.moveWithUndo(
    feed: InboxFeedState,
    message: MailMessage,
    target: MoveTarget,
    leaving: Set<String>,
) {
    val move = move(feed, message, target, leaving)
    toasts.moved(target) { moveBack(feed, message, target, leaving, after = move) }
}

/**
 * Puts [message] back in [returning], the folders a move to [target] took it out of, such as the
 * inbox after an archive or all its folders after a trash, then reloads [feed] so it shows again.
 * It waits for [after], the move itself. A toast's Undo runs it in work that outlives the
 * activity, so the outcome also reloads the screen on show.
 */
internal suspend fun MailEdits.moveBack(
    feed: InboxFeedState,
    message: MailMessage,
    target: MoveTarget,
    returning: Set<String>,
    after: Job? = null,
) {
    after?.join()
    val done = writer.moveBack(message, target.folderId, returning)
    if (done) feed.refresh()
    undone(done)
}

/**
 * Adds [message] to the folder [folderId], named [folderName], or takes it out, as Read later does.
 * When the change [leaves] the list [feed] shows, the message goes from it at once.
 */
internal fun MailEdits.setInFolder(
    feed: InboxFeedState,
    message: MailMessage,
    folderId: String,
    folderName: String,
    saved: Boolean,
    leaves: Boolean,
) {
    val edit =
        if (leaves) {
            leaveEdit(feed, message)
        } else {
            MailEdit(
                message.id,
                message.memberMailboxes.keys,
                row = { it.inFolder(folderId, saved) },
                undo = { it.inFolder(folderId, !saved) },
            )
        }
    save(edit, feed, "Couldn't change $folderName. Try again.") { writer.setInFolder(message, folderId, saved) }
}
