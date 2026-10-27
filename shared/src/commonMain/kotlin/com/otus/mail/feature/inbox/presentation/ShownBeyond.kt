package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.FilterSort
import com.otus.mail.feature.inbox.domain.MailMessage

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
    val boundary = lastOrNull()?.sortKey
    return this +
        shown.filter { message ->
            message.id !in ids &&
                boundary?.let { if (sort == FilterSort.NewestFirst) message.sortKey < it else message.sortKey > it } !=
                false
        }
}

// A conversation sorts by its newest email; local date and time strings sort as text.
private val MailMessage.sortKey: String
    get() = (conversation?.last?.received ?: received).let { "${it.date} ${it.time}" }
