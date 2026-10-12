package com.otus.mail.feature.inbox.domain

/**
 * Takes messages out of Spam and Trash on the server. A message deleted for good leaves only the
 * folder it is deleted from: one that is also in another folder stays there, as an IMAP expunge
 * leaves the message's other copies.
 */
interface MessageCleaner {
    /**
     * Moves [message] from the Spam folder it is shown in to [inboxId] of the same account. With
     * [keywords], it also sets `$notjunk` and clears `$junk`. False when the server refused or
     * could not be reached.
     */
    suspend fun notSpam(
        message: MailMessage,
        inboxId: String,
        keywords: Boolean,
    ): Boolean

    /** Deletes [messages] for good from the folder each is shown in; returns the [MailMessage.id]s deleted. */
    suspend fun delete(messages: List<MailMessage>): Set<String>

    /** Deletes every message in [folderId] of the login [accountId] for good. */
    suspend fun empty(
        accountId: String,
        folderId: String,
    ): Deletion
}

/** What emptying a folder did: the server ids of the messages that left it, and whether any stayed. */
data class Deletion(
    val emailIds: Set<String>,
    val complete: Boolean,
)
