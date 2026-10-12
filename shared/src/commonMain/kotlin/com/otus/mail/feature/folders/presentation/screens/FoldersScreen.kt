package com.otus.mail.feature.folders.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.avatar.generatedAvatar
import com.otus.designsystem.components.ChipAvatar
import com.otus.designsystem.components.CollapsibleTree
import com.otus.designsystem.components.PageNote
import com.otus.designsystem.components.RowDensity
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.components.SettingsSection
import com.otus.designsystem.components.Subpage
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_create_new_folder
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.canCreateIn
import com.otus.mail.feature.folders.domain.folderTree
import com.otus.mail.feature.folders.domain.sendersByFolder
import com.otus.mail.feature.folders.presentation.FolderDialog
import com.otus.mail.feature.folders.presentation.FolderSender
import com.otus.mail.feature.folders.presentation.FolderSenders
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.folders.presentation.RulesState
import com.otus.mail.feature.folders.presentation.components.FolderActions
import com.otus.mail.feature.folders.presentation.components.FolderDialogs
import com.otus.mail.feature.folders.presentation.components.FolderRow
import com.otus.mail.feature.folders.presentation.message

/**
 * Each account's folders as a tree under the account's name and avatar, with the senders that
 * rules move into each folder. Tapping a folder offers what the server allows for it.
 */
@Composable
fun FoldersScreen(
    state: FoldersState,
    rules: RulesState,
    senders: FolderSenders,
    onBack: () -> Unit,
    onOpenFolder: (MailFolder) -> Unit,
) {
    var dialog by rememberSaveable(stateSaver = FolderDialog.Saver) { mutableStateOf<FolderDialog?>(null) }
    LaunchedEffect(Unit) {
        state.refresh()
        rules.refresh()
    }
    LaunchedEffect(rules.accounts) { senders.resolve(rules.accounts) }
    Subpage("Folders", onBack) {
        state.notice?.let { PageNote(it) }
        if (state.accounts.isEmpty()) {
            PageNote(if (state.loading) "Loading folders…" else "Connect an account in Settings to manage its folders.")
        }
        state.accounts.forEach { account ->
            val byFolder = rules.account(account.accountId)?.sendersByFolder().orEmpty()
            AccountFolderSection(
                account,
                state.busy,
                sendersOf = { folderId -> senders.of(account.accountId, byFolder[folderId].orEmpty()) },
                onDialog = { dialog = it },
            )
        }
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
    sendersOf: (folderId: String) -> List<FolderSender>,
    onDialog: (FolderDialog) -> Unit,
) {
    val avatar = generatedAvatar(account.accountName, "")
    SettingsSection(account.accountName, ChipAvatar(avatar.initials, avatar.colorIndex)) {
        account.failure?.let { PageNote(it.message()) }
        CollapsibleTree(account.folderTree(), { it.folder.id }, { it.depth }) { node, expanded, onExpandedChange ->
            FolderRow(node, sendersOf(node.folder.id), expanded, onExpandedChange) {
                onDialog(FolderDialog.Actions(account.accountId, node.folder.id))
            }
        }
        if (account.failure == null && canCreateIn(account, null)) {
            // While a change is saving, the row stays visible but does nothing.
            SettingRow(
                if (busy) "Saving…" else "New folder",
                { onDialog(FolderDialog.Create(account.accountId, null)) }.takeUnless { busy },
                icon = Res.drawable.symbol_create_new_folder,
                density = RowDensity.Compact,
            )
        }
    }
}
