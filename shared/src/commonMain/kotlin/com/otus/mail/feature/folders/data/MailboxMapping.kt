package com.otus.mail.feature.folders.data

import com.otus.jmap.JmapException
import com.otus.jmap.JmapJson
import com.otus.jmap.SetError
import com.otus.mail.core.mail.MailCapability
import com.otus.mail.core.mail.MailFailure
import com.otus.mail.core.mail.toMailFailure
import com.otus.mail.core.mailbox.FolderFailure
import com.otus.mail.core.mailbox.FolderLimits
import kotlinx.serialization.json.JsonObject

/** A malformed capability keeps the defaults; the server still enforces its real limits. */
internal fun limits(capability: JsonObject?): FolderLimits {
    val mail =
        capability?.let { runCatching { JmapJson.decodeFromJsonElement(MailCapability.serializer(), it) }.getOrNull() }
            ?: return FolderLimits()
    return FolderLimits(
        mayCreateTopLevel = mail.mayCreateTopLevelMailbox,
        maxDepth = mail.maxMailboxDepth?.coerceIn(1, Int.MAX_VALUE.toLong())?.toInt(),
        maxNameBytes = mail.maxSizeMailboxName?.coerceIn(1, Int.MAX_VALUE.toLong())?.toInt(),
        maxFoldersPerMessage = mail.maxMailboxesPerEmail?.coerceIn(1, Int.MAX_VALUE.toLong())?.toInt(),
    )
}

internal fun JmapException.toFolderFailure(): FolderFailure =
    when (toMailFailure()) {
        MailFailure.SignInRejected -> FolderFailure.SignInRejected
        MailFailure.NotPermitted -> FolderFailure.NotPermitted
        MailFailure.CredentialsUnavailable -> FolderFailure.CredentialsUnavailable
        MailFailure.NoMail -> FolderFailure.NoMail
        MailFailure.Unreachable -> FolderFailure.Unreachable
        MailFailure.ServerProblem -> FolderFailure.ServerProblem
    }

internal fun SetError.toFolderFailure(): FolderFailure =
    when (type) {
        "alreadyExists" -> FolderFailure.AlreadyExists
        "forbidden" -> FolderFailure.NotPermitted
        "invalidProperties", "willDestroy" -> FolderFailure.Invalid
        "mailboxHasChild" -> FolderFailure.HasSubfolders
        "mailboxHasEmail" -> FolderFailure.HasMail
        "notFound" -> FolderFailure.NotFound
        else -> FolderFailure.ServerProblem
    }
