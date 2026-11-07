package com.otus.mail.feature.mailsettings.presentation

import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.presentation.ownFolders
import com.otus.mail.core.settings.domain.InboxContents

/** How the Inbox shows row names [contents], such as "Inbox", "Inbox + Spam", or "All mail" with everything on. */
fun inboxSummary(contents: InboxContents): String {
    val extras =
        listOfNotNull(
            MailboxRole.Archive.title.takeIf { contents.archive },
            "Your folders".takeIf { contents.ownFolders },
            MailboxRole.Junk.title.takeIf { contents.spam },
            MailboxRole.Trash.title.takeIf { contents.trash },
        )
    return if (extras.size ==
        EVERY_EXTRA
    ) {
        "All mail"
    } else {
        (listOf(MailboxRole.Inbox.title) + extras).joinToString(" + ")
    }
}

private const val EVERY_EXTRA = 4
