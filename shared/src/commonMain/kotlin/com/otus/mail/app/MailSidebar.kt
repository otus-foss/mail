package com.otus.mail.app

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.Sidebar
import com.otus.designsystem.components.SidebarState
import com.otus.designsystem.elements.SidebarEntry
import com.otus.designsystem.elements.SidebarHeading
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_inbox
import com.otus.designsystem.resources.symbol_settings
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.FolderNode
import com.otus.mail.feature.folders.domain.INBOX_ROLE
import com.otus.mail.feature.folders.domain.folderTree
import com.otus.mail.feature.folders.domain.readLaterTree
import com.otus.mail.feature.folders.presentation.folderIcon

/** The unified Inbox, then each account's folders, then Settings at the bottom. */
@Composable
internal fun MailSidebar(
    state: SidebarState,
    current: MailPage,
    accounts: List<AccountFolders>,
    onSelect: (MailPage) -> Unit,
) {
    Sidebar(
        state,
        title = "Otus",
        footer = {
            SidebarEntry("Settings", Res.drawable.symbol_settings, current == MailPage.Settings, {
                onSelect(MailPage.Settings)
            })
        },
    ) {
        SidebarEntry("Inbox", Res.drawable.symbol_inbox, current == MailPage.Inbox, { onSelect(MailPage.Inbox) })
        // With one account, its inbox is the unified Inbox above, and its Read later folder follows
        // with its subfolders nested under it. The inbox's subfolders move up a level.
        val single = accounts.singleOrNull()
        val lifted = single?.readLaterTree().orEmpty()
        val liftedIds = lifted.map { it.folder.id }.toSet()
        lifted.forEach { node -> FolderEntry(node, current, onSelect) }
        accounts.forEach { account ->
            val nodes =
                account
                    .folderTree { single != null && it.role == INBOX_ROLE }
                    .filter { it.folder.id !in liftedIds }
            if (nodes.isNotEmpty()) SidebarHeading(account.accountName)
            nodes.forEach { node -> FolderEntry(node, current, onSelect) }
        }
    }
}

@Composable
private fun FolderEntry(
    node: FolderNode,
    current: MailPage,
    onSelect: (MailPage) -> Unit,
) {
    val page = MailPage.Folder(node.folder.accountId, node.folder.id)
    SidebarEntry(
        node.folder.name,
        folderIcon(node),
        selected = current == page,
        onClick = { onSelect(page) },
        depth = node.depth,
        badge =
            node.folder.unread
                .takeIf { it > 0 }
                ?.toString(),
    )
}
