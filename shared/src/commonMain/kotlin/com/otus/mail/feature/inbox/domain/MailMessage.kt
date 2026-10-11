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

/** Whether [flag] is currently on for this message. */
fun MailMessage.hasFlag(flag: MessageFlag): Boolean =
    when (flag) {
        MessageFlag.Read -> !attributes.unread
        MessageFlag.Flagged -> attributes.flagged
    }
