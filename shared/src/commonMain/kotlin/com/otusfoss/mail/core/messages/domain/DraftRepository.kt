package com.otusfoss.mail.core.messages.domain

/** Saves drafts to an account's Drafts folder; [DraftSender] sends them. */
interface DraftRepository {
    /** Addresses the account may send from, from JMAP Identity/get; empty when the server has none. */
    suspend fun identities(accountId: String): List<SendIdentity>

    /**
     * Stores [draft] in the Drafts folder, replacing its earlier copy, and returns the new Email id.
     * A failed result holds a [DraftException] and leaves the earlier copy in place.
     */
    suspend fun save(draft: Draft): Result<String>

    /** Destroys the saved copy [emailId] of a draft. A failed result holds a [DraftException]. */
    suspend fun discard(
        accountId: String,
        emailId: String,
    ): Result<Unit>
}
