package com.otus.mail.core.messages.domain

import kotlinx.serialization.Serializable

/**
 * A message being written. [body] is what the user wrote as plain text and [html] the same with
 * its formatting, empty while it has none. [quote] is the original a reply quotes or a forward
 * carries, which follows the body and the [signature]. [emailId] is the server's copy in the Drafts
 * folder once saved; JMAP Email content cannot change, so each save replaces that copy.
 * [inReplyTo] and [references] keep a reply in its thread. [attachments] go after the body as files.
 * [messageId] is the Message-ID a send gives it, so a send whose answer was lost can be found.
 */
@Serializable
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
    val messageId: String? = null,
)

/** Why a draft could not be saved or discarded. */
enum class DraftFailure { NoDraftsFolder, NotPermitted, Unreachable, ServerProblem }

/** The failure a draft save or load carries. */
class DraftException(
    val failure: DraftFailure,
    cause: Throwable? = null,
) : Exception("The draft could not be saved: $failure", cause)
