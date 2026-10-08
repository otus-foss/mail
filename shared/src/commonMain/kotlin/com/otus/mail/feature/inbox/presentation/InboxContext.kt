package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.InboxSearch

/**
 * What a message list shows by default and how it names itself. The unified inbox is the default;
 * a folder's page shows all of its messages and names the folder.
 */
data class InboxContext(
    val defaultSearch: InboxSearch = InboxSearch(),
    val searchPrompt: String = "Tap to search",
    val emptyTitle: String = "Your inbox is empty",
) {
    companion object {
        fun folder(name: String): InboxContext =
            InboxContext(
                defaultSearch = InboxSearch(mailboxes = emptySet()),
                searchPrompt = "Search $name",
                emptyTitle = "$name is empty",
            )
    }
}
