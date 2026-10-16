package com.otus.mail.feature.folders.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.PageNote
import com.otus.designsystem.components.RuleChipLine
import com.otus.designsystem.components.Subpage
import com.otus.designsystem.elements.IconButton
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_add
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.canCreateIn
import com.otus.mail.feature.folders.domain.placeableChildren
import com.otus.mail.feature.folders.domain.scopeOf
import com.otus.mail.feature.folders.domain.sharedPlacements
import com.otus.mail.feature.folders.presentation.FolderColorsState
import com.otus.mail.feature.folders.presentation.FolderDialog
import com.otus.mail.feature.folders.presentation.FolderListRow
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.folders.presentation.components.AccountFolderGroup
import com.otus.mail.feature.folders.presentation.components.FolderAccountPicker
import com.otus.mail.feature.folders.presentation.components.FolderActions
import com.otus.mail.feature.folders.presentation.components.FolderDialogs
import com.otus.mail.feature.folders.presentation.components.FolderListActions
import com.otus.mail.feature.folders.presentation.components.FolderReorderTree
import com.otus.mail.feature.folders.presentation.folderRows
import com.otus.mail.feature.folders.presentation.message
import com.otus.mail.feature.folders.presentation.placement

/**
 * Settings › Folders: every account's folders as trees, or one account's when the picker chooses
 * it. Tapping a folder opens its settings through [onOpenFolder]; holding one drags it to a new
 * place or into another folder. [rulesOf] gives each folder's rules as chip lines, first rule first.
 */
@Composable
fun FoldersScreen(
    state: FoldersState,
    colors: FolderColorsState,
    rulesOf: (MailFolder) -> List<RuleChipLine>,
    onBack: () -> Unit,
    onOpenFolder: (MailFolder) -> Unit,
) {
    var dialog by rememberSaveable(stateSaver = FolderDialog.Saver) { mutableStateOf<FolderDialog?>(null) }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    var collapsed by rememberSaveable(stateSaver = KeysSaver) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(Unit) { state.refresh() }
    val accounts = state.accounts
    val one = accounts.singleOrNull() ?: accounts.firstOrNull { it.accountId == chosen }
    val groups =
        remember(accounts, one) {
            val placements = sharedPlacements(accounts)
            (one?.let(::listOf) ?: accounts).map { it to it.folderRows(placements, allAccounts = one == null) }
        }
    val tree = FolderTreeState(collapsed, { collapsed = it }, state.listActions(colors, rulesOf, onOpenFolder))
    Subpage("Folders", onBack) {
        if (accounts.size > 1) FolderAccountPicker(accounts, one, groups.sumOf { it.second.size }) { chosen = it }
        state.notice?.let { PageNote(it) }
        if (accounts.isEmpty()) {
            PageNote(if (state.loading) "Loading folders…" else "Connect an account in Settings to manage its folders.")
        }
        val newFolder = { account: AccountFolders -> newFolderButton(account, state.busy) { dialog = it } }
        // With one account the hint holds its New folder button; each account's row holds it otherwise.
        if (accounts.isNotEmpty()) FolderHint(one?.let(newFolder))
        groups.forEach { (account, rows) ->
            val key = ACCOUNT_KEY + account.accountId
            if (one == null) {
                AccountFolderGroup(
                    account,
                    rows.size,
                    key !in collapsed,
                    { tree.toggle(key, it) },
                    newFolder(account),
                ) {
                    AccountFolders(account, rows, tree)
                }
            } else {
                AccountFolders(account, rows, tree)
            }
        }
    }
    dialog?.let { open ->
        FolderDialogs(open, state.account(open.accountId), state.dialogActions()) {
            dialog =
                it
        }
    }
}

/** The collapsed rows and account groups by key, and what the folder rows do. */
private class FolderTreeState(
    val collapsed: Set<String>,
    val onCollapsedChange: (Set<String>) -> Unit,
    val actions: FolderListActions,
) {
    fun toggle(
        key: String,
        expanded: Boolean,
    ) = onCollapsedChange(if (expanded) collapsed - key else collapsed + key)
}

@Composable
private fun AccountFolders(
    account: AccountFolders,
    rows: List<FolderListRow>,
    tree: FolderTreeState,
) {
    account.failure?.let { PageNote(it.message()) }
    FolderReorderTree(rows, tree.collapsed, tree.onCollapsedChange, tree.actions)
}

/** How to use the page, with the New folder [button] at its end when one account is shown. */
@Composable
private fun FolderHint(button: (@Composable () -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { PageNote("Tap a folder to edit it, drag to reorder.") }
        button?.invoke()
    }
}

/**
 * A plus that creates a top-level folder in [account], or null where the account can't take one.
 * While another change saves, it does nothing.
 */
private fun newFolderButton(
    account: AccountFolders,
    busy: Boolean,
    onDialog: (FolderDialog) -> Unit,
): (@Composable () -> Unit)? =
    if (account.failure == null && canCreateIn(account, null)) {
        {
            IconButton("New folder", {
                onDialog(FolderDialog.Create(account.accountId, null))
            }, enabled = !busy, outlined = false) {
                Symbol(Res.drawable.symbol_add)
            }
        }
    } else {
        null
    }

private fun FoldersState.listActions(
    colors: FolderColorsState,
    rulesOf: (MailFolder) -> List<RuleChipLine>,
    onOpen: (MailFolder) -> Unit,
) = FolderListActions(
    colorOf = colors::colorOf,
    rulesOf = rulesOf,
    placeable = {
        folder,
        parentId,
        ->
        account(folder.accountId)?.scopeOf(folder)?.placeableChildren(parentId).orEmpty()
    },
    canDrop = { placement(it.folder, it.parentId, it.index) != null },
    onDrop = { place(it.folder, it.parentId, it.index) },
    onOpen = onOpen,
)

/** Only New folder opens a popup here; the folder's own page offers the other changes. */
private fun FoldersState.dialogActions() =
    FolderActions(
        onCreate = { accountId, name, parentId -> create(accountId, name, parentId) },
        onRename = ::rename,
        onDelete = ::delete,
        onMove = { folder, parentId -> place(folder, parentId) },
        color = { null },
    )

private const val ACCOUNT_KEY = "account:"

private val KeysSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })
