package com.otus.mail.feature.inbox.domain

import com.otus.avatar.ResolvedAvatar

/** Portable, supplied message data. No account connection or persistence is implied. */
data class MailMessage(
    val id: String,
    val sender: MailSender,
    val subject: String,
    val body: String,
    val received: ReceivedAt,
    val location: MailLocation,
    val attributes: MailAttributes = MailAttributes(),
    /** The server's Email id; [id] adds the account so ids from different accounts never collide. */
    val emailId: String = "",
    /** Other folders the message is in, named on its row; the app adds them once folders load. */
    val labels: List<MessageLabel> = emptyList(),
    val to: List<MailSender> = emptyList(),
    val cc: List<MailSender> = emptyList(),
    /** RFC 5322 Message-ID and References values, which keep a reply in its thread. */
    val messageIds: List<String> = emptyList(),
    val references: List<String> = emptyList(),
)

/** A folder named on a message's row. [readLater] marks the Read later folder. */
data class MessageLabel(
    val name: String,
    val readLater: Boolean = false,
)

data class MailSender(
    val name: String,
    val address: String,
    val accountKey: String = "",
    val avatar: ResolvedAvatar? = null,
)

/** ISO calendar date (YYYY-MM-DD) and 24-hour time (HH:mm), supplied by the data source. */
data class ReceivedAt(
    val date: String,
    val time: String,
)

/** The inbox's name in the app. Servers name and translate it differently, so it is found by role. */
const val INBOX_MAILBOX = "Inbox"

/** The name messages and search use for a mailbox: [INBOX_MAILBOX] for the inbox role, else the server's [name]. */
fun mailboxName(
    name: String,
    role: String?,
): String = if (role == "inbox") INBOX_MAILBOX else name

data class MailLocation(
    val mailbox: String,
    val account: String,
    /** Stable login identity; the display name is not unique. */
    val accountId: String = account,
    /** The address the account signs in with, used to show it once and look up its avatar. */
    val accountAddress: String = "",
    val accountAvatar: ResolvedAvatar? = null,
    /** The server's id for [mailbox]; empty for supplied messages. */
    val mailboxId: String = "",
    /** Every mailbox the message is in on the server, [mailboxId] among them; empty for supplied messages. */
    val mailboxIds: Set<String> = emptySet(),
)

data class MailAttributes(
    val unread: Boolean = false,
    val attachments: List<String> = emptyList(),
    val flagged: Boolean = false,
)

/** A mark the user can turn on or off for one message. */
enum class MessageFlag { Read, Flagged }

/** [message] with [flag] turned [on], as the server will show it once the change is saved. */
fun MailMessage.withFlag(
    flag: MessageFlag,
    on: Boolean,
): MailMessage =
    when (flag) {
        MessageFlag.Read -> copy(attributes = attributes.copy(unread = !on))
        MessageFlag.Flagged -> copy(attributes = attributes.copy(flagged = on))
    }

/** [message] added to [folderId], or taken out of it, as the server will show it once the change is saved. */
fun MailMessage.inFolder(
    folderId: String,
    inFolder: Boolean,
): MailMessage {
    val folders = if (inFolder) location.mailboxIds + folderId else location.mailboxIds - folderId
    return copy(location = location.copy(mailboxIds = folders))
}

/** Whether [flag] is currently on for this message. */
fun MailMessage.hasFlag(flag: MessageFlag): Boolean =
    when (flag) {
        MessageFlag.Read -> !attributes.unread
        MessageFlag.Flagged -> attributes.flagged
    }
