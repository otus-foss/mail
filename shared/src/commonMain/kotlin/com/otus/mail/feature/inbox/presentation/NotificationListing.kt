package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.snapshotFlow
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.listing
import kotlinx.coroutines.flow.first

/**
 * The row that lists the email [emailId] of [loginId], as a tapped notification asks: the feed goes
 * back to the page's own mail and loads again, and the row is looked up once neither the feed nor
 * [busy] still loads. Null when the message left every inbox or was deleted meanwhile.
 */
suspend fun InboxFeedState.reloadedListing(
    loginId: String,
    emailId: String,
    busy: () -> Boolean,
): MailMessage? {
    show(null)
    refresh()
    snapshotFlow { loading || busy() }.first { !it }
    return messages.listing(loginId, emailId)
}

/** The notice when the message a notification names has left every inbox. */
const val LEFT_INBOX_NOTICE = "This message is no longer in your inbox"
