package com.otus.mail.feature.folders.presentation

import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.messages.presentation.EditedList
import com.otus.mail.core.messages.presentation.FolderCleanup
import com.otus.mail.core.messages.presentation.FolderEmptying
import com.otus.mail.core.messages.presentation.MailEdits
import com.otus.mail.core.messages.presentation.delete
import com.otus.mail.core.messages.presentation.empty
import com.otus.mail.core.messages.presentation.notSpam

/**
 * Not spam, deleting for good, and emptying Spam and Trash where the folders allow them, as [edits]
 * that show at once; messages the server keeps come back, with a notice.
 */
internal class FolderCleaning(
    private val folders: FoldersState,
    private val edits: MailEdits,
) {
    /** The cleanup actions for the messages [feed] shows. */
    fun cleanup(feed: EditedList): FolderCleanup =
        FolderCleanup(
            role = ::role,
            listRole = { listRole(feed) },
            // Spam keywords change only where the folder allows marks; the move happens either way.
            onNotSpam = { message, inbox ->
                edits.notSpam(feed, message, inbox, keywords = folders.rightsOf(message).maySetKeywords)
            },
            canDelete = ::canDelete,
            onDelete = { edits.delete(feed, it) },
            emptying = { emptying(feed) },
        )

    private fun role(message: MailMessage): MailboxRole? =
        folders.folder(message.location.accountId, message.location.mailboxId)?.role

    private fun listRole(feed: EditedList): MailboxRole? =
        shownFolders(feed.mailScope)
            .map { it.role }
            .distinct()
            .singleOrNull()

    /** Only loaded mail has a server id, and the folder must let messages leave. */
    private fun canDelete(message: MailMessage): Boolean =
        message.emailId.isNotEmpty() &&
            role(message)?.isBin == true &&
            folders.rightsOf(message).mayRemoveItems

    /** Empties only the list's folders that let their messages leave. */
    private fun emptying(feed: EditedList): FolderEmptying? {
        val role = listRole(feed)?.takeIf { it != MailboxRole.Sent } ?: return null
        return shownFolders(feed.mailScope)
            .filter { it.rights.mayRemoveItems }
            .takeIf { it.isNotEmpty() }
            ?.let { folders -> FolderEmptying(role, folders.sumOf(MailFolder::total)) { edits.empty(feed, folders) } }
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
