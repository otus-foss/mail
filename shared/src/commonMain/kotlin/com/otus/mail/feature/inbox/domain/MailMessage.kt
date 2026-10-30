package com.otus.mail.feature.inbox.domain

import com.otus.avatar.ResolvedAvatar
import com.otus.mail.core.mail.INBOX_ROLE

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
    /** Other folders the message is in, shown on its row; the app adds them once folders load. */
    val labels: List<RowFolder> = emptyList(),
    val to: List<MailSender> = emptyList(),
    val cc: List<MailSender> = emptyList(),
    val replyTo: List<MailSender> = emptyList(),
    /** RFC 5322 Message-ID and References values, which keep a reply in its thread. */
    val messageIds: List<String> = emptyList(),
    val references: List<String> = emptyList(),
    /**
     * Whether [body] is only the start of the message, because the server sent no more of it. A cut
     * that more text follows is marked inside [body] instead.
     */
    val bodyTruncated: Boolean = false,
    /** The server's short plain-text preview of the message. The list loads it in place of the full body. */
    val preview: String = "",
    /** The thread this row stands for; null for supplied messages, which list one by one. */
    val conversation: MailConversation? = null,
)

/**
 * When the conversation's newest email arrived, a reply the user just sent among them. It dates and
 * sorts the row, so a reply moves the conversation to the top.
 */
internal val MailMessage.lastReceived: ReceivedAt
    get() = conversation?.last?.received ?: received

/**
 * A folder shown on a message's row. [color] is the saved folder color's name, null while it has
 * none or Folder colors is off. [readLater] marks the Read later folder, which the row shows as a
 * bookmark instead of a folder.
 */
data class RowFolder(
    val id: String,
    val name: String,
    val kind: FolderKind = FolderKind.Folder,
    val color: String? = null,
    val readLater: Boolean = false,
)

enum class FolderKind { Folder, Inbox }

/** [brandDomain] is the From domain the message passed DMARC for, which lets its avatar show a brand logo. */
data class MailSender(
    val name: String,
    val address: String,
    val accountKey: String = "",
    val avatar: ResolvedAvatar? = null,
    val brandDomain: String? = null,
)

/** The sender's name, or the address when the name is blank. */
internal val MailSender.shownName: String
    get() = name.ifBlank { address }

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
): String = if (role == INBOX_ROLE) INBOX_MAILBOX else name

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
    /** The attachment names of the message itself. */
    val attachments: List<String> = emptyList(),
    val flagged: Boolean = false,
    /** The message carries a calendar invitation, a `text/calendar` part or an `.ics` file. */
    val invite: Boolean = false,
    /** The message has the `$answered` keyword: someone replied to it from this account. */
    val answered: Boolean = false,
    /** The message has the `$forwarded` keyword. */
    val forwarded: Boolean = false,
    /**
     * Whether any email of the conversation has a file other than a calendar invitation, even when
     * this one has none. Invitations get their own mark from [invite] and [MailConversation.invite].
     */
    val hasAttachment: Boolean = attachments.isNotEmpty(),
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
