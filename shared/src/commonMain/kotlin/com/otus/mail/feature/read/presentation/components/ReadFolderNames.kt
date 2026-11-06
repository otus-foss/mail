package com.otus.mail.feature.read.presentation.components

import com.otus.designsystem.mail.folders.components.FolderName
import com.otus.designsystem.theme.colors.FolderColor
import com.otus.mail.core.mailbox.presentation.symbol
import com.otus.mail.core.messages.domain.MessageFolder
import com.otus.mail.core.messages.domain.displayPaths

/** [folder] as a strip or details row names it, showing [path]: its own color, or its role's outlined icon. */
internal fun folderName(
    folder: MessageFolder,
    path: List<String> = folder.path,
): FolderName =
    FolderName(
        path,
        folder.color?.let(FolderColor::named),
        folder.system,
        folder.role?.symbol,
    )

/** [folders] named as a strip names several: leaves only, or full paths where leaves clash. */
internal fun folderNames(
    folders: List<MessageFolder>,
    full: Boolean = false,
): List<FolderName> = folders.zip(folders.displayPaths(full), ::folderName)
