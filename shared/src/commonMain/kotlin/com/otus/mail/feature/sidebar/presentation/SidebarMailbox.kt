package com.otus.mail.feature.sidebar.presentation

import com.otus.designsystem.mail.folders.elements.folderSymbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_bookmark
import com.otus.designsystem.resources.symbol_contacts
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.resources.symbol_forum
import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.mailbox.SharedFolder
import com.otus.mail.core.mailbox.combinedFolders
import com.otus.mail.core.mailbox.sharedFolders
import com.otus.mail.core.settings.domain.InboxContents
import com.otus.mail.core.settings.domain.PinSize
import com.otus.mail.core.settings.domain.SidebarLayout
import com.otus.mail.core.settings.domain.SidebarPin
import org.jetbrains.compose.resources.DrawableResource

/**
 * A sidebar mailbox, the same for every account, holding each account's [folders] of its kind.
 * A pinned folder of the user's own is [shared], so its icon takes the folder's color. In the grid
 * it spans [size].
 */
internal data class SidebarMailbox(
    val title: String,
    val icon: DrawableResource,
    val pin: SidebarPin,
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
    val pinnable = pinnableMailboxes(accounts, inbox).associateBy { it.pin }
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
): List<SidebarMailbox> =
    buildList {
        val inboxFolders =
            accounts.flatMap { account ->
                account.folders.filter { it.role in inbox.roles || (inbox.ownFolders && it.isCustom) }
            }
        add(
            SidebarMailbox(
                MailboxRole.Inbox.title,
                folderSymbol(MailboxRole.Inbox.wire),
                SidebarPin.Inbox,
                inboxFolders,
            ),
        )
        val readLater = accounts.mapNotNull { it.readLater }
        if (readLater.isNotEmpty()) {
            add(
                SidebarMailbox("Read later", Res.drawable.symbol_bookmark, SidebarPin.ReadLater, readLater),
            )
        }
        // Conversations has no folder of its own, so it shows no count.
        if (accounts.any { account -> account.folders.any { it.role == MailboxRole.Sent } }) {
            add(
                SidebarMailbox("Convos", Res.drawable.symbol_forum, SidebarPin.Conversations, emptyList()),
            )
        }
        add(
            SidebarMailbox("Contacts", Res.drawable.symbol_contacts, SidebarPin.Contacts, emptyList()),
        )
        combinedFolders(accounts).forEach { (role, folders) ->
            add(
                SidebarMailbox(role.title, folderSymbol(role.wire), SidebarPin.Role(role), folders),
            )
        }
        sharedFolders(accounts).flatMap { it.flatten() }.forEach { folder ->
            add(
                SidebarMailbox(
                    folder.name,
                    Res.drawable.symbol_folder,
                    SidebarPin.Folder(folder.key),
                    folder.folders,
                    folder,
                ),
            )
        }
    }
