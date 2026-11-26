package com.otusfoss.mail.feature.inbox.domain

import com.otusfoss.mail.core.messages.domain.FolderRef
import com.otusfoss.mail.core.messages.domain.MailScope

/**
 * The scope a search loads. A search on a folder page or folder pill keeps to its
 * [defaultFolders] until [filters] choose other folders, narrowed to the search's accounts; null
 * while nothing narrows it, so the feed stays on the page's own scope. Other searches load
 * [countScope].
 */
fun InboxSearch.searchScope(
    defaultFolders: Set<FolderRef>,
    filters: InboxFilters,
): MailScope? =
    when {
        defaultFolders.isEmpty() || filters.hasFolderOverride -> {
            countScope()
        }

        accounts.isEmpty() -> {
            null
        }

        else -> {
            MailScope.Folders(
                defaultFolders
                    .filter { it.loginId in accounts }
                    .map { MailScope.Folder(it.loginId, it.mailboxId) }
                    .toSet(),
            )
        }
    }

/** The scope a search counts in: all mail but Spam and Trash, of the chosen accounts if any. */
fun InboxSearch.countScope(): MailScope =
    if (mailboxes.isEmpty() && accounts.isEmpty()) {
        MailScope.AllMail
    } else {
        MailScope.Mailboxes(mailboxes, accounts)
    }
