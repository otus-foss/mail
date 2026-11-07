package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.presentation.icon
import com.otus.mail.core.settings.presentation.icon
import com.otus.mail.feature.inbox.domain.InboxFilters

/** The page's loaded [messages] and the [filters] applied to them. */
internal class ListedMail(
    val messages: List<MailMessage>,
    val filters: InboxFilters,
) {
    /** How many loaded conversations are unread, which the Unread pill shows. */
    val unreadCount: Int by lazy { messages.count { it.attributes.unread } }
}

/**
 * [listed]'s unread count, or the last one shown while the list reloads empty. A new filter or
 * scope empties the list until mail arrives, and a count of 0 for that frame would swap the
 * Unread pill's count for its icon.
 */
@Composable
internal fun rememberUnreadCount(
    listed: ListedMail,
    loading: Boolean,
): Int {
    val reloading = loading && listed.messages.isEmpty()
    val shown = remember { mutableIntStateOf(listed.unreadCount) }
    if (!reloading) SideEffect { shown.intValue = listed.unreadCount }
    return if (reloading) shown.intValue else listed.unreadCount
}
