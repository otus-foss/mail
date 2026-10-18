package com.otus.mail.feature.inbox.domain

/**
 * A message being written. [body] is what the user wrote as plain text and [html] the same with
 * its formatting, empty while it has none. [quote] is the original a reply quotes or a forward
 * carries, which follows the body and the [signature]. [emailId] is the server's copy in the Drafts folder once saved;
 * JMAP Email content cannot change, so each save replaces that copy. [inReplyTo] and [references]
 * keep a reply in its thread. [attachments] go after the body as files.
 */
data class Draft(
    val accountId: String,
    val from: MailSender? = null,
    val to: List<MailSender> = emptyList(),
    val cc: List<MailSender> = emptyList(),
    val bcc: List<MailSender> = emptyList(),
    val subject: String = "",
    val body: String = "",
    val html: String = "",
    val quote: String = "",
    val signature: Signature = Signature(),
    val inReplyTo: List<String> = emptyList(),
    val references: List<String> = emptyList(),
    val emailId: String? = null,
    val attachments: List<DraftAttachment> = emptyList(),
)

/** Why a draft could not be saved or discarded. */
enum class DraftFailure { NoDraftsFolder, NotPermitted, Unreachable, ServerProblem }

/** The failure a [DraftRepository] result carries. */
class DraftException(
    val failure: DraftFailure,
    cause: Throwable? = null,
) : Exception("The draft could not be saved: $failure", cause)

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
