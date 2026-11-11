package com.otusfoss.mail.core.messages.domain

import com.otusfoss.mail.core.mailbox.MailboxRole

/** A file attached to a message, as the Read view lists it. [size] is in bytes. */
data class MailAttachment(
    val name: String,
    val type: String,
    val size: Long,
    val blobId: String,
)

/**
 * How to leave the mailing list a message came from, read from its `List-Unsubscribe` header
 * (RFC 2369). [web] is an address to open, [mail] a mailto address.
 * [deliveredTo] holds the addresses the mail was delivered to, from `Delivered-To` and
 * `X-Original-To`, which a mailto request should come from when the list wrote to a Bcc.
 */
data class Unsubscribe(
    val web: String? = null,
    val mail: String? = null,
    val deliveredTo: List<String> = emptyList(),
)

/**
 * A mailbox a message can be in, as the Read view names it: [path] runs from the top folder to this
 * one. [role] is null for the user's own folders. [color] is the saved folder color's name.
 * [readLater] marks the Read later folder, which the bookmark shows instead.
 */
data class MessageFolder(
    val id: String,
    val path: List<String>,
    val role: MailboxRole? = null,
    val color: String? = null,
    val readLater: Boolean = false,
) {
    val name: String get() = path.last()
    val system: Boolean get() = role != null
}

/** When remote images in a message load. See Settings › Privacy & security. */
enum class RemoteImages { Always, Known, Never }

/**
 * A mailing list a message came from. [name] is the list or sender name, [perWeek] how many
 * mails it sends a week, null when too few arrived to tell. [olderIds] are the server ids of the
 * list's older mails in the account.
 */
data class MailingList(
    val name: String,
    val perWeek: Double?,
    val olderIds: List<String>,
)

/** Blocks senders with a server-side rule that files their mail as spam. */
fun interface SenderBlocks {
    /** False when the account has no rules or the server refused. */
    suspend fun block(message: MailMessage): Boolean
}
