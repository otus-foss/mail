package com.otus.mail.feature.folders.presentation.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.mail.designsystem.components.PageNote
import com.otus.mail.designsystem.components.PageSection
import com.otus.mail.designsystem.components.Subpage
import com.otus.mail.designsystem.elements.ActionButton
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.canCreateIn
import com.otus.mail.feature.folders.domain.folderTree
import com.otus.mail.feature.folders.presentation.FolderDialog
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.folders.presentation.components.FolderActions
import com.otus.mail.feature.folders.presentation.components.FolderDialogs
import com.otus.mail.feature.folders.presentation.components.FolderRow
import com.otus.mail.feature.folders.presentation.message

/** Each account's folders as a tree. Tapping a folder offers what the server allows for it. */
@Composable
fun FoldersScreen(
    state: FoldersState,
    onBack: () -> Unit,
    onOpenFolder: (MailFolder) -> Unit,
) {
    var dialog by rememberSaveable(stateSaver = FolderDialog.Saver) { mutableStateOf<FolderDialog?>(null) }
    LaunchedEffect(Unit) { state.refresh() }
    Subpage("Folders", onBack) {
        state.notice?.let { PageNote(it) }
        if (state.accounts.isEmpty()) {
            PageNote(if (state.loading) "Loading folders…" else "Connect an account in Settings to manage its folders.")
        }
        state.accounts.forEach { account -> AccountFolderSection(account, state.busy) { dialog = it } }
        PageNote("Folders are stored on your mail server, so other mail apps show them too.")
    }
    dialog?.let { open ->
        FolderDialogs(
            open,
            state.account(open.accountId),
            FolderActions(
                onCreate = { accountId, name, parentId -> state.create(accountId, name, parentId) },
                onRename = state::rename,
                onMove = state::move,
                onDelete = state::delete,
                onOpen = onOpenFolder,
            ),
            onDialog = { dialog = it },
        )
    }
}

@Composable
private fun AccountFolderSection(
    account: AccountFolders,
    busy: Boolean,
    onDialog: (FolderDialog) -> Unit,
) {
    PageSection(account.accountName) {
        account.failure?.let { PageNote(it.message()) }
        folderTree(account.folders).forEach { node ->
            FolderRow(node) { onDialog(FolderDialog.Actions(account.accountId, node.folder.id)) }
        }
        if (account.failure == null && canCreateIn(account, null)) {
            ActionButton(
                if (busy) "Saving…" else "New folder",
                { onDialog(FolderDialog.Create(account.accountId, null)) },
                Modifier.fillMaxWidth(),
                enabled = !busy,
            )
        }
    }
}
