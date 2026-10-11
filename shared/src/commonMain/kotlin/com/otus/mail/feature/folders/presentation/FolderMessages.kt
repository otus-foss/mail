package com.otus.mail.feature.folders.presentation

import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_archive
import com.otus.designsystem.resources.symbol_bookmark
import com.otus.designsystem.resources.symbol_delete
import com.otus.designsystem.resources.symbol_draft
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.resources.symbol_inbox
import com.otus.designsystem.resources.symbol_report
import com.otus.designsystem.resources.symbol_send
import com.otus.mail.feature.folders.domain.FolderFailure
import com.otus.mail.feature.folders.domain.FolderNameProblem
import com.otus.mail.feature.folders.domain.FolderNode
import org.jetbrains.compose.resources.DrawableResource

fun FolderFailure.message(): String =
    when (this) {
        FolderFailure.AlreadyExists -> "A folder with that name already exists there."
        FolderFailure.NotPermitted -> "The server doesn't allow this change."
        FolderFailure.Invalid -> "The server refused this folder. Check its name and where it goes."
        FolderFailure.HasSubfolders -> "Move or delete its subfolders first."
        FolderFailure.HasMail -> "This folder still has mail. Move or delete its messages first."
        FolderFailure.NotFound -> "That folder no longer exists."
        FolderFailure.NoMail -> "This account doesn't offer mail over JMAP."
        FolderFailure.SignInRejected -> "The server rejected the sign-in. $RECONNECT"
        FolderFailure.CredentialsUnavailable -> "The saved password couldn't be read. $RECONNECT"
        FolderFailure.Unreachable -> "Couldn't reach the server. Try again."
        FolderFailure.ServerProblem -> "The server returned an error. Try again."
    }

private const val RECONNECT = "Reconnect the account in Settings."

fun FolderNameProblem.message(): String =
    when (this) {
        FolderNameProblem.Empty -> "Enter a name."
        FolderNameProblem.TooLong -> "This server doesn't allow names that long."
        FolderNameProblem.Taken -> "A folder with that name already exists there."
    }

/** System folders and Read later have their own symbols; the user's other folders share one. */
fun folderIcon(node: FolderNode): DrawableResource =
    if (node.readLater) Res.drawable.symbol_bookmark else roleIcon(node.folder.role)

private fun roleIcon(role: String?): DrawableResource =
    when (role) {
        "inbox" -> Res.drawable.symbol_inbox
        "drafts" -> Res.drawable.symbol_draft
        "sent" -> Res.drawable.symbol_send
        "archive" -> Res.drawable.symbol_archive
        "junk" -> Res.drawable.symbol_report
        "trash" -> Res.drawable.symbol_delete
        else -> Res.drawable.symbol_folder
    }
