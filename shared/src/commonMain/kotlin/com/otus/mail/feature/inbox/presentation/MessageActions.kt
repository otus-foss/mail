package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.MailMessage

/** A folder an open message can move to; [label] is its full path. */
data class MoveTarget(
    val folderId: String,
    val label: String,
)

/**
 * What the user can do with an open message besides reading it. The app supplies these from its
 * folders and rules; a message without targets or rule support offers neither.
 */
class MessageActions(
    val moveTargets: (MailMessage) -> List<MoveTarget>,
    val onMove: (MailMessage, MoveTarget) -> Unit,
    val canCreateRule: (MailMessage) -> Boolean,
    /** Starts a rule for mail from this message's sender. */
    val onCreateRule: (MailMessage) -> Unit,
)
