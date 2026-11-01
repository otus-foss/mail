package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.otus.dateformat.currentLocalDate
import com.otus.designsystem.shared.pages.components.PageNote
import com.otus.designsystem.shared.people.components.PersonAvatar
import com.otus.designsystem.shared.people.components.PersonRow
import com.otus.designsystem.shared.rows.components.LocalEntriesOnRaised
import com.otus.designsystem.shared.rows.elements.SectionCaption
import com.otus.designsystem.theme.tokens.layout.PanelTokens
import com.otus.designsystem.theme.tokens.overlays.SheetTokens
import com.otus.designsystem.theme.tokens.rows.EntryTokens
import com.otus.designsystem.theme.tokens.rows.InboxTokens
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.searchMessages
import com.otus.mail.feature.inbox.domain.shownName
import com.otus.mail.feature.inbox.presentation.conversationRows

/** The people whose name or address matches, who narrow the results to their mail, then every hit. */
@Composable
internal fun SearchMatches(
    room: SearchRoom,
    modifier: Modifier,
) {
    val state = room.state
    val sources = room.sources
    val applied = sources.filters.applied
    val hits =
        remember(
            sources.messages,
            state.matching,
            applied,
        ) { searchMessages(sources.messages, state.matching, applied) }
    val people = remember(hits, state.search.query) { matchingPeople(hits, state.search.query) }
    val today = currentLocalDate()
    val look = sources.page.rowLook
    val rows =
        remember(hits, look, room.dateFormatter, today) { conversationRows(hits, look, room.dateFormatter, today) }
    // The panel is raised, so the rows take the page's color to stand out from it.
    CompositionLocalProvider(LocalEntriesOnRaised provides true) {
        LazyColumn(
            modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = InboxTokens.pageInset, end = InboxTokens.pageInset),
            verticalArrangement = Arrangement.spacedBy(EntryTokens.gap),
        ) {
            if (people.isNotEmpty()) item(key = PEOPLE_KEY) { SearchPeople(room, people) }
            if (hits.isEmpty()) item(key = EMPTY_KEY) { PageNote("No matching messages") }
            items(hits, key = { it.id }) { message ->
                rows[message.id]?.let { ConversationRow(message, it) { room.gestures.tap(message) } }
            }
        }
    }
}

@Composable
private fun SearchPeople(
    room: SearchRoom,
    people: List<MailSender>,
) {
    val state = room.state
    Column(Modifier.padding(horizontal = SheetTokens.inset - InboxTokens.pageInset)) {
        SectionCaption("People")
        people.forEach { person ->
            val fromThem = {
                state.update(state.search.copy(query = "", senders = setOf(person.address)))
                state.showResults()
            }
            PersonRow(person.shownName, person.address, fromThem) {
                PersonAvatar(person.chipAvatar, PanelTokens.stackAvatarSize)
            }
        }
    }
}

private const val PEOPLE_KEY = "people"
private const val EMPTY_KEY = "empty"
