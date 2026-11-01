package com.otus.mail.app

import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.inbox.domain.FolderChange
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.hasFlag
import com.otus.mail.feature.inbox.domain.memberMailboxes
import com.otus.mail.feature.inbox.domain.withFlag
import com.otus.mail.feature.inbox.domain.withMailboxes
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.MoveTarget
import com.otus.mail.feature.inbox.presentation.PageChanges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Changes the Read view makes to single emails of a conversation that [feed] lists as one row. Each
 * reaches the server for that email alone; the row follows at once when it stands for that email
 * only, and otherwise reloads once the server saved the change, since its marks combine the rest.
 */
internal class ReadPageChanges(
    private val services: MailServices,
    private val folders: FoldersState,
    private val toasts: MoveToasts,
    private val scope: CoroutineScope,
    private val feed: InboxFeedState,
) : PageChanges {
    override fun mark(
        row: MailMessage,
        page: MailMessage,
        flag: MessageFlag,
        on: Boolean,
        onRefused: () -> Unit,
    ) {
        val before = page.hasFlag(flag)
        val alone = row.memberMailboxes.keys == setOf(page.emailId)
        val change =
            if (alone) feed.change(row.id, { it.withFlag(flag, on) }, undo = { it.withFlag(flag, before) }) else null
        scope.launch {
            if (page.id in services.marker.mark(listOf(page.copy(conversation = null)), flag, on)) {
                if (change != null) change.confirm() else feed.refresh()
                if (flag == MessageFlag.Read) folders.refresh()
            } else {
                change?.rollBack()
                onRefused()
                feed.report("Couldn't change the message. Try again.")
            }
        }
    }

    /** Trash takes the email out of every folder, as deleting a whole row does. */
    override fun trash(
        row: MailMessage,
        page: MailMessage,
        target: MoveTarget,
        onBack: () -> Unit,
    ) {
        val email = page.copy(conversation = null)
        val leaving = email.location.mailboxIds + email.location.mailboxId - ""
        val change =
            feed.change(row.id, { it.without(email.emailId) }, undo = { it.copy(conversation = row.conversation) })
        var moved = false
        val move =
            scope.saveChange(change, feed, folders, "Couldn't delete the message. Try again.", onRefused = onBack) {
                moved = services.mover.move(email, target.folderId, leaving)
                moved
            }
        toasts.moved(target) {
            move.join()
            if (!moved) return@moved
            if (services.mover.moveBack(email, target.folderId, leaving)) {
                onBack()
                feed.refresh()
                folders.refresh()
            } else {
                feed.report("Couldn't move the message back from ${target.label}. Try again.")
            }
        }
    }

    /** Sends every email's whole new folder set together; any email the server refuses undoes all. */
    fun refile(
        change: FolderChange,
        onRefused: () -> Unit,
    ) {
        val row = change.row
        val local =
            if (change.leavesList) {
                feed.remove(row.id)
            } else {
                feed.change(row.id, { it.withMailboxes(change.after) }, undo = { it.withMailboxes(change.before) })
            }
        scope.launch {
            val updated = services.mover.setMailboxes(row.location.accountId, change.after)
            if (updated.containsAll(change.after.keys)) {
                local.confirm()
                folders.refresh()
            } else {
                local.rollBack()
                // Emails the server did change go back too, so the conversation stays as it was.
                services.mover.setMailboxes(row.location.accountId, change.before.filterKeys { it in updated })
                onRefused()
                // A removed row comes back only in the list it left, and at its end; a reload puts it in place.
                feed.refresh()
                feed.report("Couldn't change the folders of this conversation. Try again.")
            }
        }
    }
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
