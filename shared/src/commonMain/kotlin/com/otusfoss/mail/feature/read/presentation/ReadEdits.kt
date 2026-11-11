package com.otusfoss.mail.feature.read.presentation

import com.otusfoss.mail.core.messages.domain.FolderChange
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MessageFlag
import com.otusfoss.mail.core.messages.domain.hasFlag
import com.otusfoss.mail.core.messages.domain.memberMailboxes
import com.otusfoss.mail.core.messages.domain.withFlag
import com.otusfoss.mail.core.messages.domain.withMailboxes
import com.otusfoss.mail.core.messages.presentation.EditedList
import com.otusfoss.mail.core.messages.presentation.MailEdit
import com.otusfoss.mail.core.messages.presentation.MailEdits
import com.otusfoss.mail.core.messages.presentation.MoveTarget
import com.otusfoss.mail.core.messages.presentation.delete
import com.otusfoss.mail.core.messages.presentation.move
import com.otusfoss.mail.core.messages.presentation.moveBack
import kotlinx.coroutines.launch

/**
 * The changes the Read view makes, for the list [feed] it was opened from. A change to one email
 * of a conversation reaches the server for that email alone; the row follows at once when it
 * stands for that email only, and otherwise reloads once the server saved the change, since its
 * marks combine the rest. The pages show every edit through [observe], wherever it was made.
 */
class ReadEdits(
    private val edits: MailEdits,
    private val feed: EditedList,
) {
    /** Sets [flag] to [on] on [page]'s email alone. */
    fun mark(
        row: MailMessage,
        page: MailMessage,
        flag: MessageFlag,
        on: Boolean,
    ) {
        val alone = row.memberMailboxes.keys == setOf(page.emailId)
        val before = page.hasFlag(flag)
        val edit =
            MailEdit(
                row.id,
                setOf(page.emailId),
                row = if (alone) { message -> message.withFlag(flag, on) } else null,
                undo = { it.withFlag(flag, before) },
                page = { it.withFlag(flag, on) },
                counts = flag == MessageFlag.Read,
            )
        edits.show(edit)
        edits.scope.launch {
            val kept = page.id in edits.writer.mark(listOf(page.copy(conversation = null)), flag, on)
            edits.settle(edit, kept)
            when {
                !kept -> feed.report("Couldn't change the message. Try again.")
                !alone -> feed.refresh()
            }
        }
    }

    /**
     * Moves [page]'s email alone to [target], the Trash, with an Undo toast. Trash takes it out of
     * every folder, as deleting a whole row does. The conversation stays on the list, and the page
     * comes back where it was when the server refuses or Undo runs.
     */
    fun trash(
        row: MailMessage,
        page: MailMessage,
        target: MoveTarget,
    ) {
        val email = page.copy(conversation = null)
        val leaving = email.location.mailboxIds + email.location.mailboxId - ""
        val edit =
            MailEdit(
                row.id,
                setOf(email.emailId),
                row = { it.without(email.emailId) },
                undo = { it.copy(conversation = row.conversation) },
                page = { null },
            )
        var moved = false
        val move =
            edits.save(edit, feed, "Couldn't delete the message. Try again.") {
                moved = edits.writer.move(email, target.folderId, leaving)
                moved
            }
        edits.toasts.moved(target) {
            move.join()
            if (!moved) return@moved
            val done = edits.writer.moveBack(email, target.folderId, leaving)
            if (done) {
                edits.revert(edit)
                feed.refresh()
            }
            edits.undone(done)
        }
    }

    /**
     * Files every email of the conversation as [change] says, in one call; when the server refuses
     * any email, the ones it did change go back too, so the conversation stays as it was.
     */
    fun refile(change: FolderChange) {
        val row = change.row
        val edit =
            MailEdit(
                row.id,
                change.after.keys,
                leaves = feed.mailScope.takeIf { change.leavesList },
                row = { it.withMailboxes(change.after) },
                undo = { it.withMailboxes(change.before) },
                page = { it.withMailboxes(change.after) },
            )
        edits.show(edit)
        edits.scope.launch {
            val updated = edits.writer.setMailboxes(row.location.accountId, change.before, change.after)
            val kept = updated.containsAll(change.after.keys)
            edits.settle(edit, kept)
            if (!kept) {
                edits.writer.setMailboxes(
                    row.location.accountId,
                    change.after,
                    change.before.filterKeys { it in updated },
                )
                // A removed row comes back only in the list it left, and at its end; a reload puts it in place.
                feed.refresh()
                feed.report("Couldn't change the folders of this conversation. Try again.")
            }
        }
    }

    suspend fun isMuted(row: MailMessage): Boolean = edits.writer.isMuted(row)

    /** False when the server refused. */
    suspend fun setMuted(
        row: MailMessage,
        muted: Boolean,
    ): Boolean = edits.writer.setMuted(row, muted)

    /** Shows every edit on [state]'s pages until the returned function runs. */
    internal fun observe(state: ReadState): () -> Unit = edits.observe(state.edits)

    /** Where a page load that starts now stands; see [MailEdits.checkpoint]. */
    internal fun checkpoint(): Int = edits.checkpoint()

    internal fun since(checkpoint: Int): List<MailEdit> = edits.since(checkpoint)
}

/**
 * The row without the email [emailId], which left on its own, as the list counts it. The row keeps
 * its last listed email until the next load, so the open conversation stays.
 */
private fun MailMessage.without(emailId: String): MailMessage =
    copy(
        conversation =
            conversation?.let { thread ->
                thread.copy(
                    messageCount = (thread.messageCount - 1).coerceAtLeast(1),
                    members = thread.members.filterNot { it.emailId == emailId }.ifEmpty { thread.members },
                )
            },
    )
