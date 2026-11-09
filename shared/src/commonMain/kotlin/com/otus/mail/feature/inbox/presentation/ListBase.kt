package com.otus.mail.feature.inbox.presentation

import com.otus.mail.core.categories.domain.InboxCategories
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.messages.domain.MailboxScope
import com.otus.mail.core.messages.domain.QuickFacet
import com.otus.mail.core.settings.domain.InboxContents
import com.otus.mail.core.settings.domain.InboxPill
import com.otus.mail.core.settings.domain.SidebarPin
import com.otus.mail.core.settings.presentation.InboxPills
import com.otus.mail.core.settings.presentation.title
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.domain.ListKind

/**
 * What a list page calls itself, by [title] and when it is empty by [emptyTitle], what its search
 * starts from and can widen to, its row of [pills], the filters it opens with, the [kind] of list
 * that decides which filters it offers, and the [categories] that sort its mail into pills, null on
 * a page that shows none.
 */
data class ListBase(
    val title: String,
    val emptyTitle: String,
    val defaultSearch: InboxSearch,
    val mailboxSearch: MailboxSearch,
    val pills: InboxPills,
    val startingFilters: InboxFilters = InboxFilters(),
    val kind: ListKind = ListKind.Folder,
    val categories: InboxCategories? = null,
) {
    companion object {
        /**
         * The Inbox, holding what the user chose in the Inbox settings, [contents]. Only it filters
         * by folder, as a folder's page already shows one, and sorts into [categories] while they
         * are on.
         */
        fun inbox(
            contents: InboxContents,
            pills: InboxPills,
            mailboxSearch: MailboxSearch,
            categories: InboxCategories,
        ): ListBase =
            ListBase(
                title = MailboxRole.Inbox.title,
                emptyTitle = "Your inbox is empty",
                defaultSearch = InboxSearch(mailboxes = contents.mailboxScopes().toSet()),
                mailboxSearch = mailboxSearch,
                pills = pills,
                kind = ListKind.Inbox,
                categories = categories.takeIf { it.enabled },
            )

        /**
         * A folder's page shows only the filter pills of [pills] that its [kind] offers: folder
         * pills would leave the folder.
         */
        fun folder(
            name: String,
            pills: List<InboxPill>,
            kind: ListKind = ListKind.Folder,
            startingFilters: InboxFilters = InboxFilters(),
        ): ListBase =
            ListBase(
                title = name,
                emptyTitle = "$name is empty",
                defaultSearch = InboxSearch(mailboxes = emptySet()),
                mailboxSearch = MailboxSearch(),
                pills = InboxPills(pills.filterIsInstance<InboxPill.Filter>().filter(kind::offers)),
                startingFilters = startingFilters,
                kind = kind,
            )

        /** Conversations, which opens on conversations only. */
        fun conversations(pills: List<InboxPill>): ListBase =
            folder(
                checkNotNull(SidebarPin.Conversations.title),
                pills,
                ListKind.Conversations,
                InboxFilters(quick = setOf(QuickFacet.Conversations)),
            )
    }
}

/**
 * Every account's inbox, and Sent: Conversations keeps threads where the user and someone else
 * both wrote. Sent lists what the user sent whether or not anyone answered.
 */
val conversationsScope: MailScope = MailScope.Roles(listOf(MailboxRole.Inbox, MailboxRole.Sent))

/**
 * What the Inbox page loads: every account's inbox, the chosen system folders under the app's
 * names, and the user's own folders when chosen.
 */
fun InboxContents.mailScope(): MailScope =
    if (roles.size == 1 && !ownFolders) {
        MailScope.Inbox
    } else {
        MailScope.Roles(roles, ownFolders)
    }

/**
 * The names the page's messages are filed under, which its default search shows. Own folders have
 * the user's names, so with them on the search starts from every mailbox the page loaded.
 */
private fun InboxContents.mailboxScopes(): List<MailboxScope> =
    if (ownFolders) emptyList() else roles.map { MailboxScope(null, it.title, it) }
