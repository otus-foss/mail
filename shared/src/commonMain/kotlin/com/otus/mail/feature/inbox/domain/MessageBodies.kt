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

/**
 * An open message's full body. [text] is the plain body, HTML read as text when the message has
 * no plain part; [truncated] when the server sent only its start. [html] is null for plain mail.
 * [attachments] are its files, without the images its HTML shows inline. [unsubscribe] is set for
 * mail from a list that can be left, and [listName] names that list when its header does.
 */
data class MessageContent(
    val text: String,
    val truncated: Boolean,
    val html: MessageHtml?,
    val attachments: List<MailAttachment> = emptyList(),
    val unsubscribe: Unsubscribe? = null,
    val listName: String? = null,
)

/** Loads the full body of an open message, since the list loads only previews. */
interface MessageBodies {
    /** The message's body; null when the server could not be reached. */
    suspend fun content(message: MailMessage): MessageContent?
}

/**
 * This message with its full text from [content], so a reply or forward quotes all of it. Without
 * content, or with an empty text, it keeps the preview it has.
 */
fun MailMessage.withContent(content: MessageContent?): MailMessage =
    if (content == null || content.text.isBlank()) {
        this
    } else {
        copy(body = content.text, bodyTruncated = content.truncated)
    }
