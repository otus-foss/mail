package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.otus.dateformat.DateFormatter
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.settings.presentation.InboxPills
import com.otus.mail.feature.inbox.presentation.ListBase
import com.otus.mail.feature.inbox.presentation.MailboxSearch
import com.otus.mail.feature.inbox.presentation.mailScope

/**
 * The unified inbox, holding what the user chose in the Inbox settings, whose folder pills load
 * in place of its own mail. A tapped notification opens its message here, in the feed
 * [MessageOpens] loads.
 */
@Composable
internal fun InboxPage(
    states: MailStates,
    dateFormatter: DateFormatter,
    navigation: MailNavigation,
) {
    val feed = states.list.feed
    val contents = states.behavior.behavior.list.inbox
    val ownScope = contents.mailScope()
    LaunchedEffect(ownScope) { feed.defaultScope = ownScope }
    val pills =
        InboxPills(
            states.behavior.behavior.list.pills,
            rememberFolderPills(states),
            foldersLoaded = !states.accounts.loading && !states.folders.all.loading,
        )
    ListPage(
        MailPage.Inbox,
        feed,
        ListBase.inbox(
            contents,
            pills,
            MailboxSearch(
                states.list.searchMailboxes(),
                states.accounts.accounts.map { MailLocation("", it.name, it.id, it.username) },
                feed::show,
            ),
            states.behavior.behavior.list.categories,
        ),
        states,
        dateFormatter,
        navigation,
        opening = states.requests.row,
    )
}
