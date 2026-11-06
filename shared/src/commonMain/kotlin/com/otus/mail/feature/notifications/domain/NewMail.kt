package com.otus.mail.feature.notifications.domain

import com.otus.mail.core.mailbox.MailboxRole
import com.otus.storage.StringMapStore
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
    /** The lowercase roles of its account's mailboxes, such as `archive`, which decide the alert's buttons. */
    val mailboxRoles: Set<MailboxRole> = emptySet(),
    /** Whether its account can send, so the alert can offer Reply. */
    val canSend: Boolean = false,
)

/** Each login's Email state from the last check, keyed by login id. */
interface MailStateStore : StringMapStore

/** A message the device has an alert for. */
data class AlertedMail(
    val loginId: String,
    val emailId: String,
)

/** The device's new mail alerts. */
interface MailAlerts {
    /** The mail with an alert still showing. */
    fun showing(): Set<AlertedMail>

    fun show(mail: List<NewMail>)

    fun clear(mail: List<AlertedMail>)
}

/** Finds mail that arrived since the last check, and alerted mail the user has handled since. */
fun interface NewMailChecker {
    /**
     * Shows the new mail of every login, oldest first, and clears the alerts of mail that was read,
     * moved out of the inbox, or deleted, on this device or another. Then saves each login's state.
     * The first check of a login only saves its state. A login that fails keeps its old state, so
     * the next check reports its mail.
     */
    suspend fun check(alerts: MailAlerts)
}
