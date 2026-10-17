package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.InboxContents

/** How the Inbox shows row names [contents]: "Inbox", "Inbox + Spam", "Inbox + Trash", or "All mail". */
fun inboxSummary(contents: InboxContents): String =
    when {
        contents.spam && contents.trash -> "All mail"
        contents.spam -> "Inbox + Spam"
        contents.trash -> "Inbox + Trash"
        else -> "Inbox"
    }
