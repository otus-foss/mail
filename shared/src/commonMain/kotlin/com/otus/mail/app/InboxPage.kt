package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.otus.dateformat.DateFormatter
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.messages.domain.MailboxScope
import com.otus.mail.core.settings.domain.InboxContents
import com.otus.mail.core.settings.presentation.InboxPills
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.MailboxSearch

/**
 * The unified inbox, holding what the user chose in the Inbox settings, whose folder pills load
 * in place of its own mail.
 */
@Composable
internal fun InboxPage(
    states: MailStates,
    dateFormatter: DateFormatter,
    navigation: MailNavigation,
) {
    val feed = states.feed
    val contents = states.behavior.behavior.inbox
    val ownScope = contents.mailScope()
    LaunchedEffect(ownScope) { feed.defaultScope = ownScope }
    val pills =
        InboxPills(
            states.behavior.behavior.pills,
            rememberFolderPills(states),
            foldersLoaded = !states.accounts.loading && !states.folders.loading,
        )
    ListPage(
        MailPage.Inbox,
        feed,
        InboxContext(
            defaultSearch = InboxSearch(mailboxes = contents.mailboxScopes().toSet()),
            pills = pills,
            mailboxSearch =
                MailboxSearch(
                    states.searchMailboxes(),
                    states.accounts.accounts.map { MailLocation("", it.name, it.id, it.username) },
                    feed::show,
                ),
        ),
        states,
        dateFormatter,
        navigation,
    )
}

/**
 * What the Inbox page loads: every account's inbox, the chosen system folders under the app's
 * names, and the user's own folders when chosen.
 */
internal fun InboxContents.mailScope(): MailScope =
    if (roles.size == 1 && !ownFolders) {
        MailScope.Inbox
    } else {
        MailScope.Roles(roles, ownFolders)
    }

/**
 * The names the page's messages are filed under, which its default search shows. Own folders have
 * the user's names, so with them on the search starts from every mailbox the page loaded.
 */
private fun InboxContents.mailboxScopes(): List<MailboxScope> =
    if (ownFolders) emptyList() else roles.map { MailboxScope(null, it.title, it) }
