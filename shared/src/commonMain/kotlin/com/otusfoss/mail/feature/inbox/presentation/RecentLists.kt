package com.otusfoss.mail.feature.inbox.presentation

import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailScope
import com.otusfoss.mail.feature.inbox.domain.MailQuery

/**
 * The last [size] lists the feed showed, by scope and query. Going back to one, such as by
 * removing the filter just added, shows it at once while the device's copy and the server catch
 * up, since reading the device's copy takes a noticeable moment.
 */
internal class RecentLists(
    private val size: Int = 4,
) {
    private var lists = emptyList<Pair<Pair<MailScope, MailQuery>, List<MailMessage>>>()

    fun of(
        scope: MailScope,
        query: MailQuery,
    ): List<MailMessage>? = lists.firstOrNull { it.first == scope to query }?.second

    fun keep(
        scope: MailScope,
        query: MailQuery,
        messages: List<MailMessage>,
    ) {
        val key = scope to query
        lists = (listOf(key to messages) + lists.filterNot { it.first == key }).take(size)
    }

    /** Applies a change made on the device to every kept list, so none shows it undone. */
    fun edit(change: (List<MailMessage>) -> List<MailMessage>) {
        lists = lists.map { (key, messages) -> key to change(messages) }
    }
}
