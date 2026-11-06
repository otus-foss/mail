package com.otus.mail.feature.inbox.presentation

import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.presentation.empty
import com.otus.mail.core.settings.domain.InboxPill
import com.otus.mail.core.settings.presentation.title
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.presentation.components.MailScopeSwitch
import com.otus.mail.feature.inbox.presentation.components.ReadView

/**
 * What a message list shows by default, how it names itself, which other mailboxes its search
 * can load, and its row of pills. The unified inbox is the default; a folder's page shows all of
 * its messages and names the folder. [folderPill] is the folder pill whose mail the page shows in
 * place of its own.
 */
data class InboxContext(
    val defaultSearch: InboxSearch = InboxSearch(),
    /** The page's name, such as "Inbox" or a folder's, which its search prompt and account switcher show. */
    val title: String = "Inbox",
    val emptyTitle: String = "Your inbox is empty",
    val mailboxSearch: MailboxSearch = MailboxSearch(),
    val pills: InboxPills = InboxPills(),
    val folderPill: FolderPillOption? = null,
    /** Opens a draft for a new message, reply, or forward. */
    val onCompose: (ComposeRequest) -> Unit = {},
    /** The page's filters, which outlive the screen; null keeps them with the screen. */
    val filters: InboxFilterState? = null,
    val filterOptions: FilterOptions = FilterOptions(),
    /** How rows look: the Mail list settings and the user's own addresses. */
    val rowLook: RowLook = RowLook(),
    /** The folder color nudge above the first row; null shows none. */
    val nudge: ListNudge? = null,
    /** Loads the feed's next page, which the list asks for as it scrolls near its end. */
    val onLoadMore: () -> Unit = {},
    /** The Read view of an opened row, over the list. */
    val readView: ReadView = ReadView {},
    /** Whose mail the page shows, which the account stack opens; null shows no stack. */
    val scope: MailScopeSwitch? = null,
    /** What pulling the control panel down shows; null keeps the panel from opening that way. */
    val quick: QuickViewContext? = null,
    /** A row the app asks the list to open, such as a tapped notification's message; null for none. */
    val opening: OpenRequest? = null,
) {
    val searchPrompt: String
        get() = "Search $title"

    val allowsMailboxSelection: Boolean
        get() = folderPill == null && defaultSearch.mailboxes.isNotEmpty()

    companion object {
        /** A folder's page shows only the filter pills of [pills]: folder pills would leave the folder. */
        fun folder(
            name: String,
            pills: List<InboxPill>,
        ): InboxContext =
            InboxContext(
                defaultSearch = InboxSearch(mailboxes = emptySet()),
                title = name,
                emptyTitle = "$name is empty",
                pills = InboxPills(pills.filterIsInstance<InboxPill.Filter>()),
            )
    }
}

/** Asks a list to open the row [id] as if it were tapped; [taken] runs once the list has. */
class OpenRequest(
    val id: String,
    val taken: () -> Unit,
)
