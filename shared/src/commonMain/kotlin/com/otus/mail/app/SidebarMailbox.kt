package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.otus.designsystem.mail.sidebar.components.SidebarMailboxGrid
import com.otus.designsystem.mail.sidebar.elements.SidebarInboxEntry
import com.otus.designsystem.mail.sidebar.elements.SidebarMailboxTile
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_bookmark
import com.otus.designsystem.resources.symbol_contacts
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.resources.symbol_forum
import com.otus.designsystem.shared.controls.elements.LocalIconTint
import com.otus.designsystem.shared.sidebar.elements.SidebarCount
import com.otus.designsystem.shared.sidebar.elements.SidebarEntry
import com.otus.designsystem.theme.colors.FolderColor
import com.otus.mail.core.mail.INBOX_ROLE
import com.otus.mail.core.mail.SENT_ROLE
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.SharedFolder
import com.otus.mail.feature.folders.domain.combinedFolders
import com.otus.mail.feature.folders.domain.sharedFolders
import com.otus.mail.feature.folders.presentation.roleIcon
import com.otus.mail.feature.inbox.domain.InboxContents
import com.otus.mail.feature.inbox.domain.MailboxStyle
import com.otus.mail.feature.inbox.domain.PinSize
import com.otus.mail.feature.inbox.domain.SidebarLayout
import com.otus.mail.feature.inbox.domain.SidebarPin
import com.otus.mail.feature.inbox.presentation.tileSize
import org.jetbrains.compose.resources.DrawableResource

/**
 * A sidebar mailbox, the same for every account, holding each account's [folders] of its kind.
 * A pinned folder of the user's own is [shared], so its icon takes the folder's color. In the grid
 * it spans [size].
 */
internal data class SidebarMailbox(
    val title: String,
    val icon: DrawableResource,
    val page: MailPage,
    val folders: List<MailFolder>,
    val shared: SharedFolder? = null,
    val size: PinSize = PinSize.Small,
)

/**
 * Each pin of [layout] that some account has, in the user's order and at its size. The Inbox counts
 * the folders [inbox] lists: each account's inbox, and the other folders chosen for it.
 */
internal fun sidebarMailboxes(
    accounts: List<AccountFolders>,
    inbox: InboxContents = InboxContents(),
    layout: SidebarLayout = SidebarLayout(),
): List<SidebarMailbox> {
    val pinnable = pinnableMailboxes(accounts, inbox).toMap()
    return layout.pinned.mapNotNull { pin -> pinnable[pin]?.copy(size = layout.sizeOf(pin)) }
}

/**
 * Every mailbox the accounts can pin, in the order Settings offers them: the Inbox, counting the
 * folders [inbox] lists, Read later while it is on,
 * Conversations once an account has a Sent folder, each system folder some account has, then the
 * user's folders, parents before their nested ones.
 */
internal fun pinnableMailboxes(
    accounts: List<AccountFolders>,
    inbox: InboxContents = InboxContents(),
): List<Pair<SidebarPin, SidebarMailbox>> =
    buildList {
        val inboxFolders =
            accounts.flatMap { account ->
                account.folders.filter { it.role in inbox.roles || (inbox.ownFolders && it.isCustom) }
            }
        add(SidebarPin.Inbox to SidebarMailbox("Inbox", roleIcon(INBOX_ROLE), MailPage.Inbox, inboxFolders))
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
        add(
            SidebarPin.Contacts to
                SidebarMailbox("Contacts", Res.drawable.symbol_contacts, MailPage.Contacts, emptyList()),
        )
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
 * Every mailbox as a tile at its size in a grid, or as rows with the Inbox as a large one, as
 * [style] chooses. [colorOf] names a pinned folder's icon color.
 */
@Composable
internal fun SidebarMailboxList(
    mailboxes: List<SidebarMailbox>,
    style: MailboxStyle,
    current: MailPage?,
    counter: SidebarCounter,
    colorOf: (SharedFolder) -> String?,
    onSelect: (MailPage) -> Unit,
) {
    when (style) {
        MailboxStyle.Grid -> {
            SidebarMailboxGrid(mailboxes, sizeOf = { it.size.tileSize }) { mailbox ->
                mailbox.Tinted(colorOf) {
                    SidebarMailboxTile(
                        mailbox.title,
                        mailbox.icon,
                        selected = current == mailbox.page,
                        onClick = { onSelect(mailbox.page) },
                        count = counter.badge(mailbox.folders),
                        size = mailbox.size.tileSize,
                    )
                }
            }
        }

        MailboxStyle.Rows -> {
            mailboxes.forEach { mailbox ->
                if (mailbox.page == MailPage.Inbox) {
                    SidebarInboxEntry(
                        mailbox.title,
                        mailbox.icon,
                        selected = current == mailbox.page,
                        onClick = { onSelect(mailbox.page) },
                        count = counter.of(mailbox.folders),
                    )
                } else {
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
}

@Composable
private fun SidebarMailbox.Tinted(
    colorOf: (SharedFolder) -> String?,
    content: @Composable () -> Unit,
) {
    val tint = shared?.let { FolderColor.named(colorOf(it)).tint }
    CompositionLocalProvider(LocalIconTint provides tint, content = content)
}
