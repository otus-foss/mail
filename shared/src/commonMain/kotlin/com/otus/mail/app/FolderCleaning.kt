package com.otus.mail.app

import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.inbox.domain.FolderRole
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.presentation.FolderCleanup
import com.otus.mail.feature.inbox.presentation.FolderEmptying
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.MoveTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Not spam, deleting for good, and emptying Spam and Trash. Each change shows at once; messages
 * the server keeps come back, with a notice.
 */
internal class FolderCleaning(
    private val services: MailServices,
    private val folders: FoldersState,
    private val scope: CoroutineScope,
) {
    /** The cleanup actions for the messages [feed] shows. */
    fun cleanup(feed: InboxFeedState): FolderCleanup =
        FolderCleanup(
            role = ::role,
            listRole = { listRole(feed) },
            onNotSpam = { message, inbox -> notSpam(feed, message, inbox) },
            canDelete = ::canDelete,
            onDelete = { delete(feed, it) },
            emptying = { emptying(feed) },
        )

    private fun role(message: MailMessage): FolderRole? =
        FolderRole.of(folders.folder(message.location.accountId, message.location.mailboxId)?.role)

    private fun listRole(feed: InboxFeedState): FolderRole? =
        shownFolders(feed.mailScope)
            .map { FolderRole.of(it.role) }
            .distinct()
            .singleOrNull()

    /** Only loaded mail has a server id, and the folder must let messages leave. */
    private fun canDelete(message: MailMessage): Boolean =
        message.emailId.isNotEmpty() &&
            role(message).let { it == FolderRole.Junk || it == FolderRole.Trash } &&
            folders.rightsOf(message).mayRemoveItems

    /** Spam keywords change only where the folder allows marks; the move happens either way. */
    private fun notSpam(
        feed: InboxFeedState,
        message: MailMessage,
        inbox: MoveTarget,
    ) {
        val change = feed.remove(message.id)
        val keywords = folders.rightsOf(message).maySetKeywords
        scope.launch {
            if (services.cleaner.notSpam(message, inbox.folderId, keywords)) {
                change.confirm()
                folders.refresh()
            } else {
                change.rollBack()
                feed.refresh()
                folders.refresh()
                feed.report("Couldn't move the message to ${inbox.label}. Try again.")
            }
        }
    }

    private fun delete(
        feed: InboxFeedState,
        messages: List<MailMessage>,
    ) {
        val changes = messages.associate { it.id to feed.remove(it.id) }
        scope.launch {
            val deleted = services.cleaner.delete(messages)
            changes.forEach { (id, change) -> if (id in deleted) change.confirm() else change.rollBack() }
            val kept = messages.size - deleted.size
            if (kept > 0) {
                feed.refresh()
                feed.report("Couldn't finish deleting ${countText(kept)}. Try again.")
            }
            folders.refresh()
        }
    }

    /** Empties only the list's folders that let their messages leave. */
    private fun emptying(feed: InboxFeedState): FolderEmptying? {
        val role = listRole(feed)?.takeIf { it != FolderRole.Sent } ?: return null
        val emptied = shownFolders(feed.mailScope).filter { it.rights.mayRemoveItems }
        return emptied.takeIf { it.isNotEmpty() }?.let {
            FolderEmptying(
                role,
                it.sumOf(MailFolder::total),
            ) { empty(feed, it) }
        }
    }

    /** Takes the loaded messages off the list, and puts back those the server kept. */
    private fun empty(
        feed: InboxFeedState,
        emptied: List<MailFolder>,
    ) {
        val loaded =
            feed.messages.filter { message ->
                emptied.any { it.accountId == message.location.accountId && it.id == message.location.mailboxId }
            }
        val changes = loaded.associateWith { feed.remove(it.id) }
        scope.launch {
            val results = emptied.map { it.accountId to services.cleaner.empty(it.accountId, it.id) }
            val removed = results.flatMap { (account, deletion) -> deletion.emailIds.map { account to it } }.toSet()
            changes.forEach { (message, change) ->
                val members = message.conversation?.members?.map { it.emailId } ?: listOf(message.emailId)
                if (members.isNotEmpty() && members.all { message.location.accountId to it in removed }) {
                    change.confirm()
                } else {
                    change.rollBack()
                }
            }
            if (results.any { !it.second.complete }) {
                feed.refresh()
                feed.report("Couldn't delete every message. Try again.")
            }
            folders.refresh()
        }
    }

    /** The loaded folders [mailScope] reads, when it names them by role or id. */
    private fun shownFolders(mailScope: MailScope): List<MailFolder> =
        when (mailScope) {
            is MailScope.Role -> folders.accounts.flatMap { it.folders }.filter { it.role == mailScope.role }
            is MailScope.Folder -> listOfNotNull(folders.folder(mailScope.accountId, mailScope.folderId))
            is MailScope.Folders -> mailScope.folders.mapNotNull { folders.folder(it.accountId, it.folderId) }
            MailScope.AllMailboxes, is MailScope.Mailboxes, is MailScope.Roles -> emptyList()
        }
}

internal fun countText(count: Int): String = if (count == 1) "1 message" else "$count messages"
