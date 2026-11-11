package com.otusfoss.mail.core.messages.presentation

import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.mailbox.pathText
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MessageFlag
import com.otusfoss.mail.core.settings.domain.ActionBehavior
import com.otusfoss.mail.core.settings.presentation.icon
import com.otusfoss.mail.core.settings.presentation.label

/**
 * Read later as offered for one message: [apply] [saves] the message or removes it. The message
 * [leaves] the list it is shown in when it moves into Read later or leaves the Read later page.
 */
class ReadLaterChange(
    val saves: Boolean,
    val leaves: Boolean,
    val apply: () -> Unit,
)

/**
 * A folder a message can move to; [folderPath] names it from the top level down and [name] is its own name. [shortcut]
 * marks the account's Inbox, Archive, Spam, or Trash, which one action files into: found by role
 * because servers name these folders differently. [own] marks the user's own folders, which the move
 * sheet lists as a tree, nested [depth] levels below other own folders. [path] is an own folder's
 * shared folder path, the names a Move to button saves; null for other folders. [color] names an
 * own folder's icon color; null keeps the plain icon.
 */
data class MoveTarget(
    val folderId: String,
    val folderPath: List<String>,
    val shortcut: MailboxRole? = null,
    val name: String = folderPath.last(),
    val depth: Int = 0,
    val own: Boolean = false,
    val path: List<String>? = null,
    val color: String? = null,
) {
    /** The full path as text, such as "Clients / Acme". */
    val label: String
        get() = folderPath.pathText()
}

/**
 * What the user can do with a message besides reading it, from an open message or by swiping its
 * row. The app supplies these from its folders, rules, and saved [ActionBehavior]; an action without a
 * target or permission is not offered.
 */
class MessageActions(
    /** Where the message can move, and the move itself. */
    val moves: MessageMoves,
    /** Which messages can take a read or flag mark, and the change itself. */
    val marks: MessageMarks,
    /** Starts a rule for mail from the message's sender; null when its account has no rules. */
    val ruleForSender: (MailMessage) -> (() -> Unit)?,
    /** Read later for the message, shown at once when applied; null while it is off or cannot change it. */
    val readLater: (MailMessage) -> ReadLaterChange?,
    /** The saved swipes and bars. */
    val saved: () -> ActionBehavior,
    /** Whether opening an unread message marks it read. */
    val markReadOnOpen: () -> Boolean,
    /** Not spam, Restore, Delete forever, and emptying; null where the page has no folders. */
    val cleanup: FolderCleanup?,
) {
    /** The folder [shortcut] files the message into; null when there is none or it is already there. */
    fun shortcutTarget(
        message: MailMessage,
        shortcut: MailboxRole,
    ): MoveTarget? = moves.targets(message).firstOrNull { it.shortcut == shortcut }

    /** Marks an unread message read as it opens, when [markReadOnOpen] asks for it. */
    fun opened(message: MailMessage) {
        val markRead = markReadOnOpen() && message.attributes.unread
        if (markRead && marks.can(message, MessageFlag.Read)) marks.opened(message)
    }
}
