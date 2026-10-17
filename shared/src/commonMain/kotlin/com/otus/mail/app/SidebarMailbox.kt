package com.otus.mail.app

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.SidebarMailboxGrid
import com.otus.designsystem.elements.SidebarCount
import com.otus.designsystem.elements.SidebarEntry
import com.otus.designsystem.elements.SidebarInboxEntry
import com.otus.designsystem.elements.SidebarMailboxTile
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_bookmark
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.INBOX_ROLE
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.combinedFolders
import com.otus.mail.feature.folders.presentation.roleIcon
import com.otus.mail.feature.inbox.domain.InboxContents
import com.otus.mail.feature.inbox.domain.MailboxStyle
import org.jetbrains.compose.resources.DrawableResource

/** A built-in mailbox, the same for every account, holding each account's [folders] of its kind. */
internal data class SidebarMailbox(
    val title: String,
    val icon: DrawableResource,
    val page: MailPage,
    val folders: List<MailFolder>,
)

/**
 * Inbox, then Read later while it is on, then each system folder some account has, in fixed order.
 * The Inbox counts the folders [inbox] lists: each chosen address's account's inbox, and its Spam
 * and Trash when chosen.
 */
internal fun sidebarMailboxes(
    accounts: List<AccountFolders>,
    inbox: InboxContents = InboxContents(),
): List<SidebarMailbox> =
    buildList {
        val inboxAccounts = inbox.addresses.map { it.accountId }.toSet()
        val inboxFolders =
            accounts
                .filter { inbox.everyAddress || it.accountId in inboxAccounts }
                .flatMap { account -> account.folders.filter { it.role in inbox.roles } }
        add(SidebarMailbox("Inbox", roleIcon(INBOX_ROLE), MailPage.Inbox, inboxFolders))
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

/** Inbox as a large row, then the other mailboxes as tiles in a grid or as rows, as [style] chooses. */
@Composable
internal fun SidebarMailboxList(
    mailboxes: List<SidebarMailbox>,
    style: MailboxStyle,
    current: MailPage,
    counter: SidebarCounter,
    onSelect: (MailPage) -> Unit,
) {
    val inbox = mailboxes.first()
    val others = mailboxes.drop(1)
    SidebarInboxEntry(
        inbox.title,
        inbox.icon,
        selected = current == inbox.page,
        onClick = { onSelect(inbox.page) },
        count = counter.of(inbox.folders),
    )
    when (style) {
        MailboxStyle.Grid -> {
            SidebarMailboxGrid(others) { mailbox ->
                SidebarMailboxTile(
                    mailbox.title,
                    mailbox.icon,
                    selected = current == mailbox.page,
                    onClick = { onSelect(mailbox.page) },
                    count = counter.badge(mailbox.folders),
                )
            }
        }

        MailboxStyle.Rows -> {
            others.forEach { mailbox ->
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
