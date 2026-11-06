package com.otus.mail.feature.inbox.presentation

import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.messages.domain.MailboxScope

/**
 * Mailboxes a page can show beyond its own messages. [mailboxes] are the search's choices, and [load]
 * fetches a search's mailboxes or a folder pill's folders, or the page's own messages again when it
 * gets null.
 */
class MailboxSearch(
    val mailboxes: List<MailboxScope> = emptyList(),
    val accounts: List<MailLocation>? = null,
    val load: (MailScope?) -> Unit = {},
)
