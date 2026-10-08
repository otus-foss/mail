package com.otus.mail.app

import androidx.compose.runtime.Composable
import com.otus.mail.designsystem.components.Sidebar
import com.otus.mail.designsystem.components.SidebarState
import com.otus.mail.designsystem.elements.SidebarEntry
import com.otus.mail.designsystem.elements.SidebarHeading
import com.otus.mail.designsystem.resources.Res
import com.otus.mail.designsystem.resources.symbol_inbox
import com.otus.mail.designsystem.resources.symbol_settings
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.INBOX_ROLE
import com.otus.mail.feature.folders.domain.folderTree
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
        // With one account, its inbox is the unified Inbox above; its subfolders move up a level.
        val single = accounts.size == 1
        accounts.forEach { account ->
            val nodes = folderTree(account.folders) { single && it.role == INBOX_ROLE }
            if (nodes.isNotEmpty()) SidebarHeading(account.accountName)
            nodes.forEach { node ->
                val page = MailPage.Folder(account.accountId, node.folder.id)
                SidebarEntry(
                    node.folder.name,
                    folderIcon(node.folder.role),
                    selected = current == page,
                    onClick = { onSelect(page) },
                    depth = node.depth,
                    badge =
                        node.folder.unread
                            .takeIf { it > 0 }
                            ?.toString(),
                )
            }
        }
    }
}
