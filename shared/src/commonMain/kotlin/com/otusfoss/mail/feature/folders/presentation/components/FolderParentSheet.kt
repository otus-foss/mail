package com.otusfoss.mail.feature.folders.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceOptions
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceRow
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceSheet
import com.otusfoss.mail.core.mailbox.AccountFolders
import com.otusfoss.mail.core.mailbox.MailFolder
import com.otusfoss.mail.feature.folders.presentation.folderPath
import com.otusfoss.mail.feature.folders.presentation.parentChoices

/**
 * Picks the folder [folder] moves into, or the top level, among the places [account] allows. Each
 * folder reads as its path, such as "Clients / Acme". Choosing one moves it at once.
 */
@Composable
internal fun FolderParentSheet(
    folder: MailFolder,
    account: AccountFolders,
    onPick: (parentId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ChoiceSheet(
        "Move into",
        ChoiceOptions(account.parentChoices(folder), account.folder(folder.parentId.orEmpty())),
        row = { ChoiceRow(it?.let { parent -> account.folderPath(parent) } ?: TOP_LEVEL) },
        onSelect = { onPick(it?.id) },
        onDismiss = onDismiss,
    )
}

internal const val TOP_LEVEL = "Top level"
