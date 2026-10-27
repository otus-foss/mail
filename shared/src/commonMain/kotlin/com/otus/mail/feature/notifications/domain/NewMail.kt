package com.otus.mail.feature.notifications.domain

import com.otus.mail.core.preferences.StringMapStore
import kotlin.time.Instant

/** A message that arrived unread in an inbox since the last check. */
data class NewMail(
    val loginId: String,
    val accountName: String,
    val emailId: String,
    val sender: String,
    val subject: String,
    val preview: String,
    val receivedAt: Instant,
    /**
     * The To and Cc addresses and those the server delivered it to, which name the user's address
     * for Bcc and list mail, for previews chosen per address.
     */
    val recipients: List<String> = emptyList(),
)

/** Each login's Email state from the last check, keyed by login id. */
interface MailStateStore : StringMapStore

/** Finds mail that arrived since the last check. */
fun interface NewMailChecker {
    /**
     * Passes the new mail of every login to [show], oldest first, then saves each login's state.
     * The first check of a login only saves its state. A login that fails keeps its old state, so
     * the next check reports its mail.
     */
    suspend fun check(show: suspend (List<NewMail>) -> Unit)
}
