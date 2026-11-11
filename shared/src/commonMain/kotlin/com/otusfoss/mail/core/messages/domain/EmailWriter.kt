package com.otusfoss.mail.core.messages.domain

/**
 * Every change Mail makes to emails on the server: marks, moves, Spam and Trash cleanup, and mutes.
 * Each change shows in the device's copy of the mail at once. A change the server cannot get now
 * waits on the device and counts as made; permanent deletion needs the server and fails offline.
 * Calls that return ids return the [MailMessage.id]s, or Email ids for [setMailboxes], the server
 * changed or that wait for it; the others were refused.
 */
interface EmailWriter {
    /** Sets [flag] to [on] for [messages] together. */
    suspend fun mark(
        messages: List<MailMessage>,
        flag: MessageFlag,
        on: Boolean,
    ): Set<String>

    /**
     * Moves [message] into [folderId] of the same account and out of [leaving]. It stays in any
     * other mailbox it is in.
     */
    suspend fun move(
        message: MailMessage,
        folderId: String,
        leaving: Set<String>,
    ): Boolean

    /**
     * Undoes a [move] of [message] into [folderId]: puts each of its emails back in the mailboxes
     * of [returning], the ones the move took it out of, that the email was in, and takes it out of
     * [folderId] unless it was already there before.
     */
    suspend fun moveBack(
        message: MailMessage,
        folderId: String,
        returning: Set<String>,
    ): Boolean

    /** Adds [message] to [folderId], or takes it out, and leaves its other mailboxes as they are. */
    suspend fun setInFolder(
        message: MailMessage,
        folderId: String,
        inFolder: Boolean,
    ): Boolean

    /** Puts each email of [mailboxes], by Email id, in exactly those mailboxes of the login [accountId]. */
    suspend fun setMailboxes(
        accountId: String,
        mailboxes: Map<String, Set<String>>,
    ): Set<String>

    /**
     * Moves [message] from the Spam folder it is shown in to [inboxId] of the same account. With
     * [keywords], it is also no longer marked as spam.
     */
    suspend fun notSpam(
        message: MailMessage,
        inboxId: String,
        keywords: Boolean,
    ): Boolean

    /**
     * Deletes [messages] for good from the folder each is shown in. A message also in another
     * folder stays there, as an IMAP expunge leaves the message's other copies.
     */
    suspend fun delete(messages: List<MailMessage>): Set<String>

    /** Deletes every message in [folderId] of the login [accountId] for good. */
    suspend fun empty(
        accountId: String,
        folderId: String,
    ): Deletion

    /** Whether new mail in [message]'s conversation stays quiet. */
    suspend fun isMuted(message: MailMessage): Boolean

    suspend fun setMuted(
        message: MailMessage,
        muted: Boolean,
    ): Boolean
}

/** What emptying a folder did: the server ids of the messages that left it, and whether any stayed. */
data class Deletion(
    val emailIds: Set<String>,
    val complete: Boolean,
)

/**
 * The row that lists the email [emailId] of the login [loginId], alone or in its conversation;
 * null when none does.
 */
fun List<MailMessage>.listing(
    loginId: String,
    emailId: String,
): MailMessage? = firstOrNull { it.location.accountId == loginId && emailId in it.memberMailboxes }
