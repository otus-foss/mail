package com.otus.mail.feature.inbox.presentation

import com.otus.mail.core.messages.presentation.ownFolders
import com.otus.mail.core.settings.domain.InboxContents

/** How the Inbox shows row names [contents], such as "Inbox", "Inbox + Spam", or "All mail" with everything on. */
fun inboxSummary(contents: InboxContents): String {
    val extras =
        listOfNotNull(
            "Archive".takeIf { contents.archive },
            "Your folders".takeIf { contents.ownFolders },
            "Spam".takeIf { contents.spam },
            "Trash".takeIf { contents.trash },
        )
    return if (extras.size == EVERY_EXTRA) "All mail" else (listOf("Inbox") + extras).joinToString(" + ")
}

private const val EVERY_EXTRA = 4
