package com.otus.mail.feature.sidebar.presentation

import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_folder
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
import com.otus.mail.core.settings.presentation.symbol
import com.otus.mail.core.settings.presentation.title
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
 * folders [inbox] lists, Read later while it is on, Conversations once an account has a Sent
 * folder, each system folder some account has, Scheduled even before one has it, then the user's
 * folders, parents before their nested ones.
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
        add(pinned(SidebarPin.Inbox, inboxFolders))
        val readLater = accounts.mapNotNull { it.readLater }
        if (readLater.isNotEmpty()) add(pinned(SidebarPin.ReadLater, readLater))
        // Conversations has no folder of its own, so it shows no count.
        if (accounts.any { account -> account.folders.any { it.role == MailboxRole.Sent } }) {
            add(pinned(SidebarPin.Conversations, emptyList()))
        }
        add(pinned(SidebarPin.Contacts, emptyList()))
        val combined = combinedFolders(accounts)
        // Scheduled shows before any account has the folder, which the first scheduled send makes.
        MailboxRole.combined
            .filter { it in combined || it == MailboxRole.Scheduled }
            .forEach { role -> add(pinned(SidebarPin.Role(role), combined[role].orEmpty())) }
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

/** The mailbox of a pin that is not one of the user's folders, under the pin's own title and symbol. */
private fun pinned(
    pin: SidebarPin,
    folders: List<MailFolder>,
): SidebarMailbox = SidebarMailbox(checkNotNull(pin.title), pin.symbol, pin, folders)
