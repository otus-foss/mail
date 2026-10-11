package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFlag

/** A folder an open message can move to; [label] is its full path. */
data class MoveTarget(
    val folderId: String,
    val label: String,
)

/** A system folder an open message can be filed into with one action. */
enum class FolderShortcut { Archive, Trash }

/**
 * What the user can do with an open message besides reading it. The app supplies these from its
 * folders and rules; an action without a target or permission is not offered.
 */
class MessageActions(
    val moveTargets: (MailMessage) -> List<MoveTarget>,
    /** The folder [FolderShortcut] files the message into; null when there is none or it is already there. */
    val shortcutTarget: (MailMessage, FolderShortcut) -> MoveTarget?,
    val onMove: (MailMessage, MoveTarget) -> Unit,
    val canMark: (MailMessage, MessageFlag) -> Boolean,
    /** Turns a mark on or off; the open message shows the change at once. */
    val onMark: (MailMessage, MessageFlag, Boolean) -> Unit,
    val canCreateRule: (MailMessage) -> Boolean,
    /** Starts a rule for mail from this message's sender. */
    val onCreateRule: (MailMessage) -> Unit,
)
