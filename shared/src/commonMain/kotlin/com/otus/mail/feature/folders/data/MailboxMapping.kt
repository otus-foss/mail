package com.otus.mail.feature.folders.data

import com.otus.jmap.JmapException
import com.otus.jmap.SetError
import com.otus.mail.core.mail.MailCapability
import com.otus.mail.core.mail.MailFailure
import com.otus.mail.core.mail.toMailFailure
import com.otus.mail.core.mailbox.FolderFailure
import com.otus.mail.core.mailbox.FolderLimits

/** A missing or malformed capability keeps the defaults; the server still enforces its real limits. */
internal fun limits(mail: MailCapability?): FolderLimits {
    if (mail == null) return FolderLimits()
    return FolderLimits(
        mayCreateTopLevel = mail.mayCreateTopLevelMailbox,
        maxDepth = mail.maxMailboxDepth?.coerceIn(1, Int.MAX_VALUE.toLong())?.toInt(),
        maxNameBytes = mail.maxSizeMailboxName?.coerceIn(1, Int.MAX_VALUE.toLong())?.toInt(),
        maxFoldersPerMessage = mail.maxMailboxesPerEmail?.coerceIn(1, Int.MAX_VALUE.toLong())?.toInt(),
    )
}

internal fun JmapException.toFolderFailure(): FolderFailure = FolderFailure.Mail(toMailFailure())

internal fun SetError.toFolderFailure(): FolderFailure =
    when (type) {
        "alreadyExists" -> FolderFailure.AlreadyExists
        "forbidden" -> FolderFailure.Mail(MailFailure.NotPermitted)
        "invalidProperties", "willDestroy" -> FolderFailure.Invalid
        "mailboxHasChild" -> FolderFailure.HasSubfolders
        "mailboxHasEmail" -> FolderFailure.HasMail
        "notFound" -> FolderFailure.NotFound
        else -> FolderFailure.Mail(MailFailure.ServerProblem)
    }
