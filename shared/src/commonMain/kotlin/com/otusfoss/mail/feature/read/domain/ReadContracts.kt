package com.otusfoss.mail.feature.read.domain

import com.otusfoss.mail.core.mail.EmailAddressKey
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailingList
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import com.otusfoss.mail.core.messages.domain.Unsubscribe

/** Loads every message of a conversation, for the Read view's pages. */
fun interface ConversationMessages {
    /**
     * Every message of [message]'s conversation, newest first, without drafts. Each has no
     * [MailMessage.conversation], so an action on it changes only that email. A message that
     * stands alone returns itself. Null when the server could not be reached.
     */
    suspend fun messages(message: MailMessage): List<MailMessage>?
}

/** The addresses the user knows on an account: their contacts and the people they wrote to. */
fun interface KnownPeople {
    /** Empty when none could be loaded. */
    suspend fun addresses(accountId: String): Set<EmailAddressKey>
}

/** What unsubscribing did. */
sealed interface UnsubscribeResult {
    /** The app sent the unsubscribe email. */
    data object Done : UnsubscribeResult

    /** The list takes no request from the app; the user finishes at [url]. */
    data class OpenLink(
        val url: String,
    ) : UnsubscribeResult

    data object Failed : UnsubscribeResult
}

/** Mailing lists: what a message's list is, leaving it, and filing its older mails. */
interface Newsletters {
    /** The list [message] came from; null when it is not from a list or the server failed. */
    suspend fun list(message: MailMessage): MailingList?

    suspend fun unsubscribe(
        message: MailMessage,
        how: Unsubscribe,
    ): UnsubscribeResult

    /** Moves [emailIds] of [accountId] to the Archive folder; false when it failed. */
    suspend fun archive(
        accountId: String,
        emailIds: List<String>,
    ): Boolean
}

/** One raw header line of a message. */
data class MailHeader(
    val name: String,
    val value: String,
)

/** A message's raw headers, for View details. */
fun interface MessageHeaders {
    /** In the order the message has them; null when the server could not be reached. */
    suspend fun headers(message: MailMessage): List<MailHeader>?
}

/** Prints a message, or saves it as a PDF, through the platform's print dialog. */
fun interface MessagePrinter {
    fun print(
        title: String,
        html: String,
    )
}

/** One of a sender's other emails, as the Read view lists it under "More from". */
data class SenderMail(
    val emailId: String,
    val subject: String,
    val preview: String,
    val received: ReceivedAt,
    val unread: Boolean,
)

/** A sender's other mail in the account of a message. */
fun interface SenderMails {
    /**
     * The newest conversations from [message]'s sender in its account, up to [SHOWN], without the
     * emails in [except] and mail in Trash or Spam; none when the server is out of reach.
     */
    suspend fun newest(
        message: MailMessage,
        except: Set<String>,
    ): List<SenderMail>

    companion object {
        /** How many conversations "More from" lists. */
        const val SHOWN: Int = 3
    }
}
