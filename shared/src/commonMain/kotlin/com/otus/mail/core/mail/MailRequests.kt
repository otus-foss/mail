package com.otus.mail.core.mail

import com.otus.jmap.JmapSort
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** `Email/query` order with the most recently received mail first (RFC 8621, section 4.4.2). */
internal val NEWEST_FIRST = listOf(JmapSort("receivedAt", isAscending = false))

/** The `Email/query` filter for the mail in mailbox [id] (RFC 8621, section 4.4.1). */
internal fun inMailbox(id: String): JsonObject = buildJsonObject { put("inMailbox", JsonPrimitive(id)) }

/** A JSON Pointer token (RFC 6901), as patch paths use (RFC 8620, section 5.3). */
internal fun pointer(id: String): String = id.replace("~", "~0").replace("/", "~1")
