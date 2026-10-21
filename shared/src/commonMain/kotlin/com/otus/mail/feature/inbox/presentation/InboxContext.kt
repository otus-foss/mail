package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.InboxPill
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageContent

/**
 * What a message list shows by default, how it names itself, which other mailboxes its search
 * can load, and its row of pills. The unified inbox is the default; a folder's page shows all of
 * its messages and names the folder. [folderPill] is the folder pill whose mail the page shows in
 * place of its own.
 */
data class InboxContext(
    val defaultSearch: InboxSearch = InboxSearch(),
    val searchPrompt: String = "Tap to search",
    val emptyTitle: String = "Your inbox is empty",
    val mailboxSearch: MailboxSearch = MailboxSearch(),
    val pills: InboxPills = InboxPills(),
    val folderPill: FolderPillOption? = null,
    /** Opens a draft for a new message, reply, or forward. */
    val onCompose: (ComposeRequest) -> Unit = {},
    /** Loads an open message's full body; null keeps the preview the list loaded. */
    val loadContent: suspend (MailMessage) -> MessageContent? = { null },
    /** The page's filters, which outlive the screen; null keeps them with the screen. */
    val filters: InboxFilterState? = null,
    val filterOptions: FilterOptions = FilterOptions(),
    /** How rows look: the Mail list settings and the user's own addresses. */
    val rowLook: RowLook = RowLook(),
    /** The folder color nudge above the first row; null shows none. */
    val nudge: ListNudge? = null,
    /** Loads the feed's next page, which the list asks for as it scrolls near its end. */
    val onLoadMore: () -> Unit = {},
    /** What an open message's Read view needs beyond [loadContent]; null shows the message alone. */
    val read: ReadContext? = null,
) {
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
                searchPrompt = "Search $name",
                emptyTitle = "$name is empty",
                pills = InboxPills(pills.filterIsInstance<InboxPill.Filter>()),
            )
    }
}
