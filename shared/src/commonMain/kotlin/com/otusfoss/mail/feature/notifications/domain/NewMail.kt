package com.otusfoss.mail.feature.notifications.domain

import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.storage.StoreSpec
import com.otusfoss.mail.core.storage.TextCodec
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * A message that arrived unread in an inbox since the last check. An alert's buttons and their
 * queued work carry it as [encoded].
 */
@Serializable
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
    /** The roles of its account's mailboxes, such as Archive, which decide the alert's buttons. */
    val mailboxRoles: Set<MailboxRole> = emptySet(),
    /** Whether its account can send, so the alert can offer Reply. */
    val canSend: Boolean = false,
) {
    /** This alert as one line of JSON. */
    fun encoded(): String = Json.encodeToString(this)

    companion object {
        // Alerts and their queued work outlive an update, so a field another build added or dropped
        // must not lose them.
        private val lenient = Json { ignoreUnknownKeys = true }

        /** The alert [encoded] made, by this build or another; null for anything else. */
        fun decoded(line: String?): NewMail? =
            line?.let {
                try {
                    lenient.decodeFromString<NewMail>(it)
                } catch (_: SerializationException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
    }
}

/**
 * Each login's Email state from the last check, keyed by login id. It is written at once, since a
 * process killed after the alerts post would otherwise post them again, dismissed ones included.
 */
object MailStateStore : StoreSpec<String, String>("states", TextCodec.Text, TextCodec.Text, commit = true)

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

    /** Clears every alert of the login [loginId], for an account that was removed. */
    fun forget(loginId: String) = clear(showing().filter { it.loginId == loginId })
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
