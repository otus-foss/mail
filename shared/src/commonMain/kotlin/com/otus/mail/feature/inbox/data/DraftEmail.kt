package com.otus.mail.feature.inbox.data

import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.MailSender
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The Email object `Email/set` creates for this draft in the [draftsId] mailbox (RFC 8621,
 * section 4.6). It is marked `$draft`, and `$seen` so the Drafts folder does not count it as unread.
 * Empty recipient and thread headers are left out rather than written empty. The body is plain
 * text, or text and [html] as alternatives; HTML with inline images puts them beside it in a
 * `multipart/related` part, which the HTML names by `cid:`.
 */
internal fun Draft.toEmail(
    draftsId: String,
    html: EmailHtml? = htmlContent()?.let { EmailHtml(it) },
): JsonObject =
    buildJsonObject {
        putJsonObject("mailboxIds") { put(draftsId, true) }
        putJsonObject("keywords") {
            put(DRAFT_KEYWORD, true)
            put(SEEN_KEYWORD, true)
        }
        from?.let { put("from", addresses(listOf(it))) }
        mapOf("to" to to, "cc" to cc, "bcc" to bcc)
            .filterValues { it.isNotEmpty() }
            .forEach { (header, senders) -> put(header, addresses(senders)) }
        put("subject", subject)
        mapOf("inReplyTo" to inReplyTo, "references" to references)
            .filterValues { it.isNotEmpty() }
            .forEach { (header, ids) -> put(header, JsonArray(ids.map(::JsonPrimitive))) }
        putJsonObject("bodyValues") {
            putJsonObject(TEXT_PART) { put("value", textContent()) }
            html?.let { putJsonObject(HTML_PART) { put("value", it.html) } }
        }
        when {
            html == null -> {
                put("textBody", JsonArray(listOf(bodyPart(TEXT_PART, "text/plain"))))
            }

            html.images.isEmpty() -> {
                put("textBody", JsonArray(listOf(bodyPart(TEXT_PART, "text/plain"))))
                put("htmlBody", JsonArray(listOf(bodyPart(HTML_PART, "text/html"))))
            }

            else -> {
                put("bodyStructure", html.structure())
            }
        }
    }

/** The HTML part of an Email and the images it shows inline, already uploaded. */
internal class EmailHtml(
    val html: String,
    val images: List<InlineImage> = emptyList(),
)

/** An uploaded image an [EmailHtml] shows by its [cid]. */
internal data class InlineImage(
    val cid: String,
    val blobId: String,
    val type: String,
)

/** Text and HTML as alternatives, the HTML beside its images in `multipart/related`. */
private fun EmailHtml.structure(): JsonObject =
    multipart(
        "multipart/alternative",
        listOf(
            bodyPart(TEXT_PART, "text/plain"),
            multipart(
                "multipart/related",
                listOf(bodyPart(HTML_PART, "text/html")) +
                    images.map { image ->
                        buildJsonObject {
                            put("blobId", image.blobId)
                            put("type", image.type)
                            put("cid", image.cid)
                            put("disposition", "inline")
                        }
                    },
            ),
        ),
    )

private fun multipart(
    type: String,
    parts: List<JsonObject>,
): JsonObject =
    buildJsonObject {
        put("type", type)
        put("subParts", JsonArray(parts))
    }

private fun bodyPart(
    partId: String,
    type: String,
): JsonObject =
    buildJsonObject {
        put("partId", partId)
        put("type", type)
    }

/** A name that is blank or only repeats the address is left out of the header. */
private fun addresses(senders: List<MailSender>): JsonArray =
    JsonArray(
        senders.map { sender ->
            buildJsonObject {
                sender.name.takeIf { it.isNotBlank() && it != sender.address }?.let { put("name", it) }
                put("email", sender.address)
            }
        },
    )

private const val TEXT_PART = "text"
private const val HTML_PART = "html"
