package com.otus.mail.feature.inbox.domain

/**
 * Marks and files one email by its id, without a list row to act on, as a notification's buttons
 * do. False when the server refused the change, the login is gone, or the account has no mailbox
 * for the change, such as no Archive.
 */
interface EmailShortcuts {
    /** Adds `$seen` to the email [emailId] of the login [loginId]. */
    suspend fun markRead(
        loginId: String,
        emailId: String,
    ): Boolean

    /** Adds `$flagged` to the email [emailId] of the login [loginId]. */
    suspend fun flag(
        loginId: String,
        emailId: String,
    ): Boolean

    /** Moves the email [emailId] of the login [loginId] out of its inbox and into the account's Archive. */
    suspend fun archive(
        loginId: String,
        emailId: String,
    ): Boolean

    /**
     * Moves the email [emailId] of the login [loginId] out of every mailbox and into the account's
     * Trash, as the list's Delete does, so it can still be restored from there.
     */
    suspend fun trash(
        loginId: String,
        emailId: String,
    ): Boolean

    /** Moves the email [emailId] of the login [loginId] out of every mailbox and into the account's Spam. */
    suspend fun reportSpam(
        loginId: String,
        emailId: String,
    ): Boolean
}

/**
 * The row that lists the email [emailId] of the login [loginId], alone or in its conversation;
 * null when none does.
 */
fun List<MailMessage>.listing(
    loginId: String,
    emailId: String,
): MailMessage? = firstOrNull { it.location.accountId == loginId && emailId in it.memberMailboxes }
