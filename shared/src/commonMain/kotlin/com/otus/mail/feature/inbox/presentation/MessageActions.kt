package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFlag

/** A folder a message can be filed into with one action. */
enum class FolderShortcut { ReadLater, Archive, Trash }

/**
 * A folder a message can move to; [label] is its full path. [shortcut] marks the account's Read
 * later folder, or its archive or trash folder, found by role because servers name these folders
 * differently.
 */
data class MoveTarget(
    val folderId: String,
    val label: String,
    val shortcut: FolderShortcut? = null,
)

/**
 * What the user can do with a message besides reading it, from an open message or by swiping its
 * row. The app supplies these from its folders, rules, and saved [behavior]; an action without a
 * target or permission is not offered.
 */
class MessageActions(
    /** Folders the message may move to: they accept mail, and its own folder lets it leave. */
    val moveTargets: (MailMessage) -> List<MoveTarget>,
    /** Moves the message; the list shows the change at once. */
    val onMove: (MailMessage, MoveTarget) -> Unit,
    val canMark: (MailMessage, MessageFlag) -> Boolean,
    /** Turns a mark on or off; the open message shows the change at once. */
    val onMark: (MailMessage, MessageFlag, Boolean) -> Unit,
    val canCreateRule: (MailMessage) -> Boolean,
    /** Starts a rule for mail from this message's sender. */
    val onCreateRule: (MailMessage) -> Unit,
    /** The saved swipes and whether opening a message marks it read. */
    val behavior: () -> MailBehavior,
) {
    /** The folder [shortcut] files the message into; null when there is none or it is already there. */
    fun shortcutTarget(
        message: MailMessage,
        shortcut: FolderShortcut,
    ): MoveTarget? = moveTargets(message).firstOrNull { it.shortcut == shortcut }

    /** Marks an unread message read as it opens, when the saved behavior asks for it. */
    fun opened(message: MailMessage) {
        val markRead = behavior().markReadOnOpen && message.attributes.unread
        if (markRead && canMark(message, MessageFlag.Read)) onMark(message, MessageFlag.Read, true)
    }
}
