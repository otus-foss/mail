package com.otus.mail.feature.inbox.domain

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
 * one. [role] is the JMAP role, null for the user's own folders. [color] is the saved folder color's
 * name. [readLater] marks the Read later folder, which the bookmark shows instead.
 */
data class MessageFolder(
    val id: String,
    val path: List<String>,
    val role: String? = null,
    val color: String? = null,
    val readLater: Boolean = false,
) {
    val name: String get() = path.last()
    val system: Boolean get() = role != null
}

/** When remote images in a message load. See Settings › Privacy & security. */
enum class RemoteImages { Always, Known, Never }

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
    /** Lowercase addresses; empty when none could be loaded. */
    suspend fun addresses(accountId: String): Set<String>
}

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

/** Mutes a conversation with the `$muted` keyword, so new mail in it does not notify. */
interface ConversationMute {
    suspend fun isMuted(message: MailMessage): Boolean

    /** False when the server refused or could not be reached. */
    suspend fun setMuted(
        message: MailMessage,
        muted: Boolean,
    ): Boolean
}

/** Blocks senders with a server-side rule that files their mail as spam. */
fun interface SenderBlocks {
    /** False when the account has no rules or the server refused. */
    suspend fun block(message: MailMessage): Boolean
}

/** The platform's file handling for attachments and other files the app makes. */
interface SavedFiles {
    /** Saves [bytes] to the device's downloads as [name]; false when it failed. */
    suspend fun save(
        name: String,
        type: String,
        bytes: ByteArray,
    ): Boolean

    /** Copies a downloaded [file] to the device's downloads; false when it failed. */
    suspend fun save(file: StoredFile): Boolean

    /** Opens a downloaded [file] in an app that shows it; false when none can. */
    suspend fun open(file: StoredFile): Boolean
}

/** Prints a message, or saves it as a PDF, through the platform's print dialog. */
fun interface MessagePrinter {
    fun print(
        title: String,
        html: String,
    )
}
