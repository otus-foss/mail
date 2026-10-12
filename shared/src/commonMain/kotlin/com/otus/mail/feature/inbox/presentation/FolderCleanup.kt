package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.FolderRole
import com.otus.mail.feature.inbox.domain.MailMessage

/**
 * What Spam, Trash, and Sent pages offer besides the usual actions. App wiring supplies it from
 * the loaded folders. Each change shows at once and is undone, with a notice, if the server refuses.
 */
class FolderCleanup(
    /** The role of the folder the message is shown in; null for other folders. */
    val role: (MailMessage) -> FolderRole?,
    /** The role every folder the list shows has; null when they differ or have none. */
    val listRole: () -> FolderRole?,
    /** Moves a message from Spam to [MoveTarget], its account's inbox. */
    val onNotSpam: (MailMessage, MoveTarget) -> Unit,
    /** Whether the message may be deleted for good from the Spam or Trash folder it is shown in. */
    val canDelete: (MailMessage) -> Boolean,
    val onDelete: (List<MailMessage>) -> Unit,
    /** Empties the Spam or Trash folders the list shows; null when it shows others or may not. */
    val emptying: () -> FolderEmptying?,
)

/** Deleting every message in the list's [role] folders, [count] of them, for good. */
class FolderEmptying(
    val role: FolderRole,
    val count: Long,
    val run: () -> Unit,
)
