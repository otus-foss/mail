package com.otus.mail.core.messages.domain

/**
 * Changes to one email, the email [emailId] of the login [loginId], without a list row to act on,
 * as a notification's buttons make them. Each shows in the device's copy at once and waits there
 * while the server cannot get it, as [EmailWriter]'s do. Each is false when the server refused,
 * or, for the moves, when the account has no folder for the change, such as no Archive.
 */
interface EmailByIdWriter {
    /** Marks the email read. */
    suspend fun markRead(
        loginId: String,
        emailId: String,
    ): Boolean

    suspend fun flag(
        loginId: String,
        emailId: String,
    ): Boolean

    /** Moves the email out of its inbox and into the account's Archive. */
    suspend fun archive(
        loginId: String,
        emailId: String,
    ): Boolean

    /** Moves the email out of every mailbox and into the account's Trash, as the list's Delete does. */
    suspend fun trash(
        loginId: String,
        emailId: String,
    ): Boolean

    /** Moves the email out of every mailbox and into the account's Spam. */
    suspend fun reportSpam(
        loginId: String,
        emailId: String,
    ): Boolean
}
