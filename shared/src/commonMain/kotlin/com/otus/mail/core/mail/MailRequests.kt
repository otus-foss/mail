package com.otus.mail.core.mail

import com.otus.jmap.JmapFilter
import com.otus.jmap.JmapSort
import com.otus.jmap.filterCondition
import kotlinx.serialization.json.JsonPrimitive

/** `Email/query` order with the most recently received mail first (RFC 8621, section 4.4.2). */
internal val NEWEST_FIRST = listOf(JmapSort("receivedAt", isAscending = false))

/** The `Email/query` filter for the mail in mailbox [id] (RFC 8621, section 4.4.1). */
internal fun inMailbox(id: String): JmapFilter = filterCondition("inMailbox", JsonPrimitive(id))
