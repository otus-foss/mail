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
        putJsonObject("bodyValues") { putJsonObject(BODY_PART) { put("value", body) } }
        put(
            "textBody",
            buildJsonArray {
                addJsonObject {
                    put("partId", BODY_PART)
                    put("type", "text/plain")
                }
            },
        )
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

private const val BODY_PART = "body"
