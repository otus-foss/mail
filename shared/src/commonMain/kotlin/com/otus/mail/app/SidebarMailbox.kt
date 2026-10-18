package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.otus.designsystem.components.SidebarMailboxGrid
import com.otus.designsystem.elements.LocalIconTint
import com.otus.designsystem.elements.SidebarCount
import com.otus.designsystem.elements.SidebarEntry
import com.otus.designsystem.elements.SidebarInboxEntry
import com.otus.designsystem.elements.SidebarMailboxTile
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_bookmark
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.resources.symbol_forum
import com.otus.designsystem.theme.FolderColor
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.INBOX_ROLE
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.SENT_ROLE
import com.otus.mail.feature.folders.domain.SharedFolder
import com.otus.mail.feature.folders.domain.combinedFolders
import com.otus.mail.feature.folders.domain.sharedFolders
import com.otus.mail.feature.folders.presentation.roleIcon
import com.otus.mail.feature.inbox.domain.InboxContents
import com.otus.mail.feature.inbox.domain.MailboxStyle
import com.otus.mail.feature.inbox.domain.SidebarPin
import org.jetbrains.compose.resources.DrawableResource

/**
 * A sidebar mailbox, the same for every account, holding each account's [folders] of its kind.
 * A pinned folder of the user's own is [shared], so its icon takes the folder's color.
 */
internal data class SidebarMailbox(
    val title: String,
    val icon: DrawableResource,
    val page: MailPage,
    val folders: List<MailFolder>,
    val shared: SharedFolder? = null,
)

/**
 * Inbox, then each of [pinned] that some account has, in the user's order. The Inbox counts the
 * folders [inbox] lists: each account's inbox, and the other folders chosen for it.
 */
internal fun sidebarMailboxes(
    accounts: List<AccountFolders>,
    inbox: InboxContents = InboxContents(),
    pinned: List<SidebarPin> = SidebarPin.defaults,
): List<SidebarMailbox> {
    val inboxFolders =
        accounts.flatMap { account ->
            account.folders.filter {
                it.role in inbox.roles ||
                    (inbox.ownFolders && it.isCustom)
            }
        }
    val pinnable = pinnableMailboxes(accounts).toMap()
    return listOf(SidebarMailbox("Inbox", roleIcon(INBOX_ROLE), MailPage.Inbox, inboxFolders)) +
        pinned.mapNotNull(pinnable::get)
}

/**
 * Every mailbox the accounts can pin, in the order Settings offers them: Read later while it is on,
 * Conversations once an account has a Sent folder, each system folder some account has, then the
 * user's folders, parents before their nested ones.
 */
internal fun pinnableMailboxes(accounts: List<AccountFolders>): List<Pair<SidebarPin, SidebarMailbox>> =
    buildList {
        val readLater = accounts.mapNotNull { it.readLater }
        if (readLater.isNotEmpty()) {
            add(
                SidebarPin.ReadLater to
                    SidebarMailbox("Read later", Res.drawable.symbol_bookmark, MailPage.ReadLater, readLater),
            )
        }
        // Conversations has no folder of its own, so it shows no count.
        if (accounts.any { account -> account.folders.any { it.role == SENT_ROLE } }) {
            add(
                SidebarPin.Conversations to
                    SidebarMailbox("Convos", Res.drawable.symbol_forum, MailPage.Conversations, emptyList()),
            )
        }
        combinedFolders(accounts).forEach { (folder, folders) ->
            add(
                SidebarPin.Role(folder.role) to
                    SidebarMailbox(folder.title, roleIcon(folder.role), MailPage.Combined(folder), folders),
            )
        }
        sharedFolders(accounts).flatMap { it.flatten() }.forEach { folder ->
            add(
                SidebarPin.Folder(folder.key) to
                    SidebarMailbox(
                        folder.name,
                        Res.drawable.symbol_folder,
                        MailPage.Shared(folder.key),
                        folder.folders,
                        folder,
                    ),
            )
        }
    }

/**
 * Inbox as a large row, then the pinned mailboxes as tiles in a grid or as rows, as [style] chooses.
 * [colorOf] names a pinned folder's icon color.
 */
@Composable
internal fun SidebarMailboxList(
    mailboxes: List<SidebarMailbox>,
    style: MailboxStyle,
    current: MailPage,
    counter: SidebarCounter,
    colorOf: (SharedFolder) -> String?,
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
        inset = style == MailboxStyle.Grid,
    )
    when (style) {
        MailboxStyle.Grid -> {
            SidebarMailboxGrid(others) { mailbox ->
                mailbox.Tinted(colorOf) {
                    SidebarMailboxTile(
                        mailbox.title,
                        mailbox.icon,
                        selected = current == mailbox.page,
                        onClick = { onSelect(mailbox.page) },
                        count = counter.badge(mailbox.folders),
                    )
                }
            }
        }

        MailboxStyle.Rows -> {
            others.forEach { mailbox ->
                mailbox.Tinted(colorOf) {
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
}

@Composable
private fun SidebarMailbox.Tinted(
    colorOf: (SharedFolder) -> String?,
    content: @Composable () -> Unit,
) {
    val tint = shared?.let { FolderColor.named(colorOf(it)).tint }
    CompositionLocalProvider(LocalIconTint provides tint, content = content)
}
