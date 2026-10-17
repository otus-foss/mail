package com.otus.mail.app

import com.otus.mail.feature.folders.domain.ARCHIVE_ROLE
import com.otus.mail.feature.folders.domain.FolderRights
import com.otus.mail.feature.folders.domain.INBOX_ROLE
import com.otus.mail.feature.folders.domain.JUNK_ROLE
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.ReadLaterStep
import com.otus.mail.feature.folders.domain.TRASH_ROLE
import com.otus.mail.feature.folders.domain.folderTree
import com.otus.mail.feature.folders.domain.labelFolders
import com.otus.mail.feature.folders.domain.readLaterStep
import com.otus.mail.feature.folders.domain.sharedFolders
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageLabel
import com.otus.mail.feature.inbox.domain.inFolder
import com.otus.mail.feature.inbox.domain.suggestedFolder
import com.otus.mail.feature.inbox.presentation.FolderShortcut
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.MoveTarget
import com.otus.mail.feature.inbox.presentation.ReadLaterChange
import com.otus.mail.feature.inbox.presentation.ownFolders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Files messages into folders and in and out of Read later. Each change shows at once and is
 * undone, with a notice, if the server refuses it.
 */
internal class MessageFiling(
    private val services: MailServices,
    private val folders: FoldersState,
    private val scope: CoroutineScope,
) {
    /** [messages] with the other folders each one is in, named for its row. */
    fun labeled(messages: List<MailMessage>): List<MailMessage> =
        messages.map { message ->
            val labels =
                folders
                    .account(message.location.accountId)
                    ?.labelFolders(message.location.mailboxIds, message.location.mailboxId)
                    ?.map { MessageLabel(it.folder.name, readLater = it.readLater) }
            message.copy(labels = labels.orEmpty())
        }

    /**
     * Folders of the message's account that accept mail, except the one it is in. The user's own
     * folders are nested as if system folders and Read later were not there.
     */
    fun moveTargets(message: MailMessage): List<MoveTarget> {
        val account = folders.account(message.location.accountId)
        if (account == null || !folders.rightsOf(message).mayRemoveItems) return emptyList()
        val own = account.folderTree { !it.isCustom || it.id == account.readLaterId }.associateBy { it.folder.id }
        val paths =
            sharedFolders(listOf(account))
                .flatMap { it.flatten() }
                .flatMap { shared -> shared.folders.map { it.id to shared.path } }
                .toMap()
        return account
            .folderTree()
            .filter { it.folder.id != message.location.mailboxId && it.folder.rights.mayAddItems }
            .map { node ->
                val ownNode = own[node.folder.id]
                MoveTarget(
                    node.folder.id,
                    node.path,
                    shortcutFor(node.folder.role),
                    name = node.folder.name,
                    depth = ownNode?.depth ?: 0,
                    own = ownNode != null,
                    path = paths[node.folder.id],
                )
            }
    }

    /** The user's own folder where most of the sender's recent mail is, read from the server. */
    suspend fun suggestedTarget(message: MailMessage): MoveTarget? {
        val own = moveTargets(message).ownFolders().associateBy { it.folderId }
        if (own.isEmpty()) return null
        return suggestedFolder(services.senderHistory.recentFolders(message), own.keys)?.let(own::get)
    }

    /**
     * Takes the message out of [feed] at once and puts it back if the server refuses the move. A
     * successful move does not reload the list; [feed] keeps the message out of loads that may
     * predate the move. The returned job ends when the server has answered.
     */
    fun move(
        feed: InboxFeedState,
        message: MailMessage,
        target: MoveTarget,
    ): Job {
        val leaving = leaving(message, target.shortcut)
        val change = feed.remove(message.id)
        return scope.launch {
            if (services.mover.move(message, target.folderId, leaving)) {
                change.confirm()
                folders.refresh()
            } else {
                change.rollBack()
                feed.report("Couldn't move the message to ${target.label}. Try again.")
            }
        }
    }

    /**
     * Puts [message] back in every folder a move to [target] took it out of, such as the inbox
     * after an archive or all its folders after a trash, then reloads [feed] so it shows again. It
     * waits for [after], the move itself, so the two cannot cross.
     */
    fun moveBack(
        feed: InboxFeedState,
        message: MailMessage,
        target: MoveTarget,
        after: Job? = null,
    ) {
        scope.launch {
            after?.join()
            if (services.mover.moveBack(message, target.folderId, leaving(message, target.shortcut))) {
                feed.refresh()
                folders.refresh()
            } else {
                feed.report("Couldn't move the message back from ${target.label}. Try again.")
            }
        }
    }

    /** Read later for a loaded message shown in [feed]; supplied messages have no server id. */
    fun readLater(
        feed: InboxFeedState,
        message: MailMessage,
    ): ReadLaterChange? {
        val account = folders.account(message.location.accountId)
        val folder = account?.readLater
        if (folder == null || message.emailId.isEmpty()) return null
        val shown = message.location.mailboxId
        return when (account.readLaterStep(message.location.mailboxIds.ifEmpty { setOf(shown) }, shown)) {
            ReadLaterStep.Save -> {
                ReadLaterChange(saves = true, leaves = false) { setReadLater(feed, message, folder, true) }
            }

            ReadLaterStep.Move -> {
                ReadLaterChange(saves = true, leaves = true) { move(feed, message, MoveTarget(folder.id, folder.name)) }
            }

            ReadLaterStep.Remove -> {
                val leaves = shown == folder.id
                ReadLaterChange(saves = false, leaves = leaves) { setReadLater(feed, message, folder, false, leaves) }
            }

            null -> {
                null
            }
        }
    }

    private fun setReadLater(
        feed: InboxFeedState,
        message: MailMessage,
        folder: MailFolder,
        saved: Boolean,
        leaves: Boolean = false,
    ) {
        val change =
            if (leaves) {
                feed.remove(message.id)
            } else {
                feed.change(message.id, { it.inFolder(folder.id, saved) }, undo = { it.inFolder(folder.id, !saved) })
            }
        scope.launch {
            if (services.mover.setInFolder(message, folder.id, saved)) {
                change.confirm()
                folders.refresh()
            } else {
                change.rollBack()
                feed.report("Couldn't change ${folder.name}. Try again.")
            }
        }
    }

    /**
     * The folders a move takes the message out of: the one it is shown in, the inbox too when it
     * is archived, and every folder when it is trashed or marked spam, so it leaves Read later as well.
     */
    private fun leaving(
        message: MailMessage,
        shortcut: FolderShortcut?,
    ): Set<String> {
        val shown = setOf(message.location.mailboxId)
        val folderIds = message.location.mailboxIds
        val inbox = folders.account(message.location.accountId)?.inbox?.id
        return when (shortcut) {
            FolderShortcut.Trash, FolderShortcut.Spam -> shown + folderIds
            FolderShortcut.Archive -> shown + setOfNotNull(inbox?.takeIf { it in folderIds })
            FolderShortcut.Inbox, null -> shown
        }
    }
}

/** Rights of the folder the message is shown in. Unknown folders allow everything; the server decides. */
internal fun FoldersState.rightsOf(message: MailMessage): FolderRights =
    folder(message.location.accountId, message.location.mailboxId)?.rights ?: FolderRights()

private fun shortcutFor(role: String?): FolderShortcut? =
    when (role) {
        INBOX_ROLE -> FolderShortcut.Inbox
        ARCHIVE_ROLE -> FolderShortcut.Archive
        JUNK_ROLE -> FolderShortcut.Spam
        TRASH_ROLE -> FolderShortcut.Trash
        else -> null
    }
