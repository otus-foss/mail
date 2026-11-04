package com.otus.mail.feature.inbox.domain

/**
 * Marks and files one email by its id, without a list row to act on, as a notification's buttons
 * do. False when the server refused the change, the login is gone, or the account has no Archive.
 */
interface EmailShortcuts {
    /** Adds `$seen` to the email [emailId] of the login [loginId]. */
    suspend fun markRead(
        loginId: String,
        emailId: String,
    ): Boolean

    /** Moves the email [emailId] of the login [loginId] out of its inbox and into the account's Archive. */
    suspend fun archive(
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
