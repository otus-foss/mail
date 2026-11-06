package com.otus.mail.feature.folders.presentation

import com.otus.designsystem.mail.folders.elements.folderSymbol
import com.otus.mail.core.mail.MailFailure
import com.otus.mail.core.mailbox.FolderFailure
import com.otus.mail.core.mailbox.FolderNode
import com.otus.mail.feature.folders.domain.FolderNameProblem
import org.jetbrains.compose.resources.DrawableResource

fun FolderFailure.message(): String =
    when (this) {
        FolderFailure.AlreadyExists -> "A folder with that name already exists there."
        FolderFailure.Invalid -> "The server refused this folder. Check its name and where it goes."
        FolderFailure.HasSubfolders -> "Move or delete its subfolders first."
        FolderFailure.HasMail -> "This folder still has mail. Move or delete its messages first."
        FolderFailure.NotFound -> "That folder no longer exists."
        is FolderFailure.Mail -> failure.message()
    }

/** The folder page's wording for a failure every Mail feature shares; rules reuse it. */
internal fun MailFailure.message(): String =
    when (this) {
        MailFailure.NotPermitted -> "The server doesn't allow this change."
        MailFailure.NoMail -> "This account doesn't offer mail over JMAP."
        MailFailure.SignInRejected -> "The server rejected the sign-in. $RECONNECT"
        MailFailure.CredentialsUnavailable -> "The saved password couldn't be read. $RECONNECT"
        MailFailure.Unreachable -> "Couldn't reach the server. Try again."
        MailFailure.ServerProblem -> "The server returned an error. Try again."
    }

private const val RECONNECT = "Reconnect the account in Settings."

fun FolderNameProblem.message(): String =
    when (this) {
        FolderNameProblem.Empty -> "Enter a name."
        FolderNameProblem.TooLong -> "This server doesn't allow names that long."
        FolderNameProblem.Taken -> "A folder with that name already exists there."
    }

/** System folders and Read later have their own symbols; the user's other folders share one. */
fun folderIcon(node: FolderNode): DrawableResource = folderSymbol(node.folder.role?.wire, node.readLater)
