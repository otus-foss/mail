package com.otus.mail.feature.inbox.domain

/**
 * A message being written. [emailId] is the server's copy in the Drafts folder once saved; JMAP
 * Email content cannot change, so each save replaces that copy. [inReplyTo] and [references] keep
 * a reply in its thread.
 */
data class Draft(
    val accountId: String,
    val from: MailSender? = null,
    val to: List<MailSender> = emptyList(),
    val cc: List<MailSender> = emptyList(),
    val bcc: List<MailSender> = emptyList(),
    val subject: String = "",
    val body: String = "",
    val inReplyTo: List<String> = emptyList(),
    val references: List<String> = emptyList(),
    val emailId: String? = null,
)

/** Why a draft could not be saved or discarded. */
enum class DraftFailure { NoDraftsFolder, NotPermitted, Unreachable, ServerProblem }

/** The failure a [DraftRepository] result carries. */
class DraftException(
    val failure: DraftFailure,
    cause: Throwable? = null,
) : Exception("The draft could not be saved: $failure", cause)

/** Saves drafts to an account's Drafts folder. Sending is not part of this contract yet. */
interface DraftRepository {
    /** Addresses the account may send from, from JMAP Identity/get; empty when the server has none. */
    suspend fun identities(accountId: String): List<MailSender>

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
