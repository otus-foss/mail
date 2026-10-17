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
 * Empty recipient and thread headers are left out rather than written empty.
 */
internal fun Draft.toEmail(draftsId: String): JsonObject =
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
        val html = htmlContent()
        putJsonObject("bodyValues") {
            putJsonObject(TEXT_PART) { put("value", textContent()) }
            html?.let { putJsonObject(HTML_PART) { put("value", it) } }
        }
        put("textBody", bodyPart(TEXT_PART, "text/plain"))
        html?.let { put("htmlBody", bodyPart(HTML_PART, "text/html")) }
    }

/** What the user wrote, then the quote after a blank line. */
internal fun Draft.textContent(): String = listOf(body, quote).filter(String::isNotBlank).joinToString("\n\n")

/**
 * The formatted body with the quote after it, its quoted lines in a blockquote; null while the
 * body has no formatting, so the message stays plain text.
 */
internal fun Draft.htmlContent(): String? =
    html.takeIf(String::isNotBlank)?.let { body -> if (quote.isBlank()) body else body + quote.quoteHtml() }

private fun String.quoteHtml(): String =
    lines()
        .chunkedBy { it.startsWith(QUOTE_MARK) }
        .joinToString("", prefix = "<br>") { (quoted, lines) ->
            if (quoted) {
                lines.joinToString(
                    "<br>",
                    "<blockquote>",
                    "</blockquote>",
                ) { it.removePrefix(QUOTE_MARK).trimStart().escapeHtml() }
            } else {
                lines.joinToString("<br>", postfix = "<br>") { it.escapeHtml() }
            }
        }

/** Runs of neighboring lines that agree on [key], in order. */
private fun List<String>.chunkedBy(key: (String) -> Boolean): List<Pair<Boolean, List<String>>> =
    fold(emptyList<Pair<Boolean, List<String>>>()) { runs, line ->
        val quoted = key(line)
        val last = runs.lastOrNull()
        if (last?.first ==
            quoted
        ) {
            runs.dropLast(1) + (quoted to last.second + line)
        } else {
            runs + (quoted to listOf(line))
        }
    }

private fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun bodyPart(
    partId: String,
    type: String,
): JsonArray =
    buildJsonArray {
        addJsonObject {
            put("partId", partId)
            put("type", type)
        }
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
private const val QUOTE_MARK = ">"
