package com.otus.mail.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import com.otus.designsystem.elements.SidebarCount
import com.otus.designsystem.elements.SidebarEntry
import com.otus.designsystem.elements.SidebarPill
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_bookmark
import com.otus.designsystem.theme.PanelTokens
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.INBOX_ROLE
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.combinedFolders
import com.otus.mail.feature.folders.presentation.roleIcon
import com.otus.mail.feature.inbox.domain.MailboxStyle
import org.jetbrains.compose.resources.DrawableResource

/** A built-in mailbox, the same for every account, holding each account's [folders] of its kind. */
internal data class SidebarMailbox(
    val title: String,
    val icon: DrawableResource,
    val page: MailPage,
    val folders: List<MailFolder>,
)

/** Inbox, then Read later while it is on, then each system folder some account has, in fixed order. */
internal fun sidebarMailboxes(accounts: List<AccountFolders>): List<SidebarMailbox> =
    buildList {
        add(SidebarMailbox("Inbox", roleIcon(INBOX_ROLE), MailPage.Inbox, accounts.mapNotNull { it.inbox }))
        val readLater = accounts.mapNotNull { it.readLater }
        if (readLater.isNotEmpty()) {
            add(
                SidebarMailbox("Read later", Res.drawable.symbol_bookmark, MailPage.ReadLater, readLater),
            )
        }
        combinedFolders(accounts).forEach { (folder, folders) ->
            add(SidebarMailbox(folder.title, roleIcon(folder.role), MailPage.Combined(folder), folders))
        }
    }

@Composable
internal fun SidebarMailboxList(
    mailboxes: List<SidebarMailbox>,
    style: MailboxStyle,
    current: MailPage,
    counter: SidebarCounter,
    onSelect: (MailPage) -> Unit,
) {
    when (style) {
        MailboxStyle.Chips -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelTokens.chipGap)) {
                mailboxes.forEach { mailbox ->
                    SidebarPill(
                        mailbox.title,
                        mailbox.icon,
                        selected = current == mailbox.page,
                        onClick = { onSelect(mailbox.page) },
                        count = counter.of(mailbox.folders),
                    )
                }
            }
        }

        MailboxStyle.Rows -> {
            mailboxes.forEach { mailbox ->
                SidebarEntry(
                    mailbox.title,
                    mailbox.icon,
                    selected = current == mailbox.page,
                    onClick = { onSelect(mailbox.page) },
                    trailing = { counter.of(mailbox.folders)?.let { SidebarCount(it) } },
                )
            }
        }
    }
}
