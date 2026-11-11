package com.otusfoss.mail.core.mail

import com.otusfoss.core.jmap.jmapPatch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The `Email/set` patch that sets [keyword], or clears it when not [on]; other keywords stay. */
internal fun keywordPatch(
    keyword: String,
    on: Boolean,
): JsonObject = membershipPatch(KEYWORDS, keyword, on)

/** The patch that adds the email to mailbox [id], or takes it out when not [member]; other mailboxes stay. */
internal fun mailboxPatch(
    id: String,
    member: Boolean,
): JsonObject = membershipPatch(MAILBOX_IDS, id, member)

/** The patch that takes an email out of the mailboxes [from] and files it in [to]. */
internal fun movePatch(
    from: Collection<String>,
    to: String,
): JsonObject =
    jmapPatch {
        from.filter { it != to }.forEach { remove(MAILBOX_IDS, it) }
        set(JsonPrimitive(true), MAILBOX_IDS, to)
    }

/**
 * The patch that puts [key] into the set-like [property] (RFC 8621, section 4.1.1), or takes it
 * out when it is no [member]. Other keys stay as they are.
 */
private fun membershipPatch(
    property: String,
    key: String,
    member: Boolean,
): JsonObject = jmapPatch { if (member) set(JsonPrimitive(true), property, key) else remove(property, key) }

private const val KEYWORDS = "keywords"
private const val MAILBOX_IDS = "mailboxIds"
