package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailScope

/**
 * Mailboxes a page can show beyond its own messages. [names] are the search's choices, and [load]
 * fetches a search's mailboxes or a folder pill's folders, or the page's own messages again when it
 * gets null.
 */
class MailboxSearch(
    val names: List<String> = emptyList(),
    val accounts: List<MailLocation>? = null,
    val load: (MailScope?) -> Unit = {},
)
