package com.otusfoss.mail.feature.inbox.domain

import com.otusfoss.mail.core.messages.domain.FolderRef
import com.otusfoss.mail.core.messages.domain.MailScope

/**
 * The scope a search loads: all non-bin mail across accounts (or within chosen search accounts)
 * while searching from Inbox or once folder filters differ from [defaultFolders]. Null while
 * searching inside a specific folder's untouched default scope, so the feed stays on it.
 */
fun InboxSearch.searchScope(
    defaultFolders: Set<FolderRef>,
    filters: InboxFilters,
): MailScope? {
    val scopedToFolder = defaultFolders.isNotEmpty() && !filters.hasFolderOverride(defaultFolders)
    val noSearchNarrowing = accounts.isEmpty() && mailboxes.isEmpty()
    if (scopedToFolder && noSearchNarrowing) return null
    return countScope()
}

/** The scope used to count or query a search's drafts: all non-bin mail (or chosen search accounts/mailboxes). */
fun InboxSearch.countScope(): MailScope =
    if (mailboxes.isEmpty() && accounts.isEmpty()) {
        MailScope.AllMail
    } else {
        MailScope.Mailboxes(mailboxes, accounts)
    }
