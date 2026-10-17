package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.InboxContents

/** How the Inbox settings row sums up [contents], such as "2 addresses · Spam". */
fun inboxSummary(contents: InboxContents): String =
    listOfNotNull(
        if (contents.everyAddress) "All mail" else addressCount(contents.addresses.size),
        "Spam".takeIf { contents.spam },
        "Trash".takeIf { contents.trash },
    ).joinToString(" · ")
