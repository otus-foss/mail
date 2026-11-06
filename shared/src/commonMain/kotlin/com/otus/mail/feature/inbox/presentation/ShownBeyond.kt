package com.otus.mail.feature.inbox.presentation

import com.otus.mail.core.messages.domain.FilterSort
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.lastReceived

/**
 * This server answer, which has more to load, followed by the rows of [shown] past its last row
 * in [sort] order. The server has not reached those yet, so they stay until a later page confirms
 * or drops them, and a partial answer never hides a match the device already showed. A row within
 * the answer's range that the answer left out is no match, and goes.
 */
internal fun List<MailMessage>.withShownBeyond(
    shown: List<MailMessage>,
    sort: FilterSort,
): List<MailMessage> {
    val ids = map { it.id }.toSet()
    val boundary = lastOrNull()?.sortKey ?: return this + shown.filterNot { it.id in ids }
    val beyond = { message: MailMessage ->
        if (sort == FilterSort.NewestFirst) message.sortKey < boundary else message.sortKey > boundary
    }
    return this + shown.filter { it.id !in ids && beyond(it) }
}

// Local date and time strings sort as text.
private val MailMessage.sortKey: String
    get() = lastReceived.let { "${it.date} ${it.time}" }
