package com.otus.mail.feature.folders.presentation

import com.otus.mail.core.mailbox.FolderNode
import com.otus.mail.core.mailbox.FolderRights
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.mailbox.folderTree
import com.otus.mail.core.mailbox.sharedFolders
import com.otus.mail.core.messages.domain.FolderKind
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MessageFlag
import com.otus.mail.core.messages.domain.RowFolder
import com.otus.mail.core.messages.domain.SenderHistory
import com.otus.mail.core.messages.domain.suggestedFolder
import com.otus.mail.core.messages.presentation.EditedList
import com.otus.mail.core.messages.presentation.MailEdits
import com.otus.mail.core.messages.presentation.MoveTarget
import com.otus.mail.core.messages.presentation.ReadLaterChange
import com.otus.mail.core.messages.presentation.move
import com.otus.mail.core.messages.presentation.moveBack
import com.otus.mail.core.messages.presentation.moveWithUndo
import com.otus.mail.core.messages.presentation.ownFolders
import com.otus.mail.core.messages.presentation.setInFolder
import com.otus.mail.feature.folders.domain.ReadLaterStep
import com.otus.mail.feature.folders.domain.labelFolders
import com.otus.mail.feature.folders.domain.readLaterStep

/**
 * Files messages into folders and in and out of Read later, as [edits] that show at once and are
 * undone, with a notice, if the server refuses them.
 */
internal class MessageFiling(
    private val senderHistory: SenderHistory,
    private val folders: FoldersState,
    private val colors: FolderColorsState,
    private val edits: MailEdits,
) {
    /**
     * [messages] with the other folders each one is in, for its row. Each account's tree is built
     * once. A message [copies] already labeled keeps that copy, so its row can skip redrawing.
     */
    fun labeled(
        messages: List<MailMessage>,
        copies: LabeledCopies = LabeledCopies(),
    ): List<MailMessage> {
        val trees by lazy { folders.accounts.associate { it.accountId to it.folderTree() } }
        val labeled = messages.map { message -> copies.of(message) ?: message.labeled(trees) }
        copies.keep(messages, labeled)
        return labeled
    }

    private fun MailMessage.labeled(trees: Map<String, List<FolderNode>>): MailMessage =
        copy(
            labels =
                trees[location.accountId]
                    ?.labelFolders(location.mailboxIds, location.mailboxId)
                    ?.map {
                        RowFolder(
                            it.folder.id,
                            it.folder.name,
                            if (it.folder.role == MailboxRole.Inbox) FolderKind.Inbox else FolderKind.Folder,
                            colors.colorOf(it.folder),
                            it.readLater,
                        )
                    }.orEmpty(),
        )

    /**
     * Folders of the message's account that accept mail, except the one it is in. The user's own
     * folders are nested as if system folders and Read later were not there.
     */
    fun moveTargets(message: MailMessage): List<MoveTarget> =
        listedTargets(message).filter { it.folderId != message.location.mailboxId }

    /** [moveTargets] with the folder the message is in, in tree order, for the move sheet's list. */
    fun listedTargets(message: MailMessage): List<MoveTarget> {
        val account = folders.account(message.location.accountId)
        if (account == null || !folders.rightsOf(message).mayRemoveItems) return emptyList()
        val own = account.folderTree { !account.isOwnFolder(it) }.associateBy { it.folder.id }
        val paths =
            sharedFolders(listOf(account))
                .flatMap { it.flatten() }
                .flatMap { shared -> shared.folders.map { it.id to shared.path } }
                .toMap()
        return account
            .folderTree()
            .filter { it.folder.id == message.location.mailboxId || it.folder.rights.mayAddItems }
            .map { node ->
                val ownNode = own[node.folder.id]
                MoveTarget(
                    node.folder.id,
                    node.path,
                    node.folder.role?.takeIf { it in SHORTCUTS },
                    name = node.folder.name,
                    depth = ownNode?.depth ?: 0,
                    own = ownNode != null,
                    path = paths[node.folder.id],
                    color = colors.colorOf(node.folder),
                )
            }
    }

    /** The user's own folder where most of the sender's recent mail is, read from the server. */
    suspend fun suggestedTarget(message: MailMessage): MoveTarget? {
        val own = moveTargets(message).ownFolders().associateBy { it.folderId }
        if (own.isEmpty()) return null
        return suggestedFolder(senderHistory.recentFolders(message), own.keys)?.let(own::get)
    }

    /** Moves [message] to [target] with the Undo toast; see [MailEdits.move]. */
    fun move(
        feed: EditedList,
        message: MailMessage,
        target: MoveTarget,
    ) = edits.moveWithUndo(feed, message, target, leaving(message, target.shortcut))

    /** Undoes a move of [message] to [target]; see [MailEdits.moveBack]. */
    suspend fun moveBack(
        feed: EditedList,
        message: MailMessage,
        target: MoveTarget,
    ) = edits.moveBack(feed, message, target, leaving(message, target.shortcut))

    /** Read later for a loaded message shown in [feed]; supplied messages have no server id. */
    fun readLater(
        feed: EditedList,
        message: MailMessage,
    ): ReadLaterChange? {
        val account = folders.account(message.location.accountId)
        val folder = account?.readLater
        if (folder == null || message.emailId.isEmpty()) return null
        val shown = message.location.mailboxId
        val target = MoveTarget(folder.id, listOf(folder.name))
        val leaves = shown == folder.id
        val save = { setReadLater(feed, message, folder, saved = true) }
        val remove = { setReadLater(feed, message, folder, saved = false, leaves) }
        return when (account.readLaterStep(message.location.mailboxIds.ifEmpty { setOf(shown) }, shown)) {
            ReadLaterStep.Save -> ReadLaterChange(saves = true, leaves = false, apply = save)
            ReadLaterStep.Move -> ReadLaterChange(saves = true, leaves = true) { moveAlone(feed, message, target) }
            ReadLaterStep.Remove -> ReadLaterChange(saves = false, leaves = leaves, apply = remove)
            null -> null
        }
    }

    private fun setReadLater(
        feed: EditedList,
        message: MailMessage,
        folder: MailFolder,
        saved: Boolean,
        leaves: Boolean = false,
    ) = edits.setInFolder(feed, message, folder.id, folder.name, saved, leaves)

    /** A move into Read later offers no Undo toast; the Read later control itself undoes it. */
    private fun moveAlone(
        feed: EditedList,
        message: MailMessage,
        target: MoveTarget,
    ) {
        edits.move(feed, message, target, leaving(message, target.shortcut))
    }

    /**
     * The folders a move takes the message out of: the one it is shown in, the inbox too when it
     * is archived, and every folder when it is trashed or marked spam, so it leaves Read later as well.
     */
    private fun leaving(
        message: MailMessage,
        shortcut: MailboxRole?,
    ): Set<String> {
        val shown = setOf(message.location.mailboxId)
        val folderIds = message.location.mailboxIds
        val inbox = folders.account(message.location.accountId)?.inbox?.id
        return when (shortcut) {
            MailboxRole.Trash, MailboxRole.Junk -> shown + folderIds
            MailboxRole.Archive -> shown + setOfNotNull(inbox?.takeIf { it in folderIds })
            else -> shown
        }
    }
}

/** Rights of the folder the message is shown in. Unknown folders allow everything; the server decides. */
internal fun FoldersState.rightsOf(message: MailMessage): FolderRights =
    folder(message.location.accountId, message.location.mailboxId)?.rights ?: FolderRights()

/** Whether [message] can take [flag]: supplied messages have no server id, and the folder must allow it. */
internal fun FoldersState.mayMark(
    message: MailMessage,
    flag: MessageFlag,
): Boolean =
    message.emailId.isNotEmpty() &&
        when (flag) {
            MessageFlag.Read -> rightsOf(message).maySetSeen
            MessageFlag.Flagged -> rightsOf(message).maySetKeywords
        }

/** The system folders one action files a message into. */
private val SHORTCUTS = setOf(MailboxRole.Inbox, MailboxRole.Archive, MailboxRole.Junk, MailboxRole.Trash)

/**
 * The copies [MessageFiling.labeled] made, by message id. Keep one only while the folders and their
 * colors stay: a message passed again unchanged, as when another sender's avatar arrives, then
 * keeps its copy.
 */
internal class LabeledCopies {
    private var copies = emptyMap<String, Pair<MailMessage, MailMessage>>()

    /** The copy made of this very [message] last time, if any. */
    fun of(message: MailMessage): MailMessage? = copies[message.id]?.takeIf { it.first === message }?.second

    fun keep(
        messages: List<MailMessage>,
        labeled: List<MailMessage>,
    ) {
        copies = messages.zip(labeled).associateBy { it.first.id }
    }
}
