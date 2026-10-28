package com.otus.mail.feature.inbox.presentation.components

import com.otus.designsystem.mail.folders.components.FolderName
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_archive
import com.otus.designsystem.resources.symbol_delete
import com.otus.designsystem.resources.symbol_draft
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.resources.symbol_inbox
import com.otus.designsystem.resources.symbol_report
import com.otus.designsystem.resources.symbol_send
import com.otus.designsystem.theme.colors.FolderColor
import com.otus.mail.feature.inbox.domain.MessageFolder
import com.otus.mail.feature.inbox.domain.displayPaths
import org.jetbrains.compose.resources.DrawableResource

/** [folder] as a strip or details row names it, showing [path]: its own color, or its role's outlined icon. */
internal fun folderName(
    folder: MessageFolder,
    path: List<String> = folder.path,
): FolderName =
    FolderName(
        path,
        folder.color?.let(FolderColor::named),
        folder.system,
        folder.role?.let(::roleIcon),
    )

/** [folders] named as a strip names several: leaves only, or full paths where leaves clash. */
internal fun folderNames(
    folders: List<MessageFolder>,
    full: Boolean = false,
): List<FolderName> = folders.zip(folders.displayPaths(full), ::folderName)

private fun roleIcon(role: String): DrawableResource =
    when (role) {
        "inbox" -> Res.drawable.symbol_inbox
        "sent" -> Res.drawable.symbol_send
        "archive" -> Res.drawable.symbol_archive
        "junk" -> Res.drawable.symbol_report
        "trash" -> Res.drawable.symbol_delete
        "drafts" -> Res.drawable.symbol_draft
        else -> Res.drawable.symbol_folder
    }
