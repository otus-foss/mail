package com.otus.mail.feature.inbox.domain

/**
 * A message's HTML body. [remoteContent] tells whether it refers to images, styles, or fonts on
 * other servers, which load only when the user asks. [truncated] when the server sent only its start.
 */
data class MessageHtml(
    val html: String,
    val remoteContent: Boolean,
    val truncated: Boolean = false,
)

/** Loads the full body of an open message. */
interface MessageBodies {
    /** The message's HTML body; null when it has none or the server could not be reached. */
    suspend fun html(message: MailMessage): MessageHtml?
}
