package com.otus.mail.feature.inbox.presentation

import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.presentation.ListNudge
import com.otus.mail.core.messages.presentation.components.MailScopeSwitch
import com.otus.mail.core.messages.presentation.empty
import com.otus.mail.core.settings.domain.InboxPill
import com.otus.mail.core.settings.presentation.FolderPillOption
import com.otus.mail.core.settings.presentation.InboxPills
import com.otus.mail.core.settings.presentation.title
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.presentation.components.ReadView

/**
 * What a message list shows by default, how it names itself, which other mailboxes its search
 * can load, its row of pills, and what its page wires in, such as the Read view and filters.
 */
data class InboxContext(
    val defaultSearch: InboxSearch,
    /** The page's name, such as "Inbox" or a folder's, which its search prompt and account switcher show. */
    val title: String,
    val emptyTitle: String,
    val mailboxSearch: MailboxSearch,
    val pills: InboxPills,
    /** Opens a draft for a new message, reply, or forward. */
    val onCompose: (ComposeRequest) -> Unit,
    /** The page's filters, which outlive the screen; null keeps them with the screen. */
    val filters: InboxFilterState?,
    val filterOptions: FilterOptions,
    /** How rows look: the Mail list settings and the user's own addresses. */
    val rowLook: RowLook,
    /** The folder color nudge above the first row; null shows none. */
    val nudge: ListNudge?,
    /** Loads the feed's next page, which the list asks for as it scrolls near its end. */
    val onLoadMore: () -> Unit,
    /** The Read view of an opened row, over the list. */
    val readView: ReadView,
    /** Whose mail the page shows, which the account stack opens; null shows no stack. */
    val scope: MailScopeSwitch?,
    /** What pulling the control panel down shows. */
    val quick: QuickViewContext,
    /** A row the app asks the list to open, such as a tapped notification's message; null for none. */
    val opening: OpenRequest?,
    /** The folder pill the screen shows in place of the page's own mail; none until the user picks one. */
    val folderPill: FolderPillOption? = null,
) {
    val searchPrompt: String
        get() = "Search $title"

    val allowsMailboxSelection: Boolean
        get() = folderPill == null && defaultSearch.mailboxes.isNotEmpty()
}

/** Asks a list to open the row [id] as if it were tapped; [taken] runs once the list has. */
class OpenRequest(
    val id: String,
    val taken: () -> Unit,
)
