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
import com.otus.designsystem.mail.quickview.components.QuickFace
import com.otus.designsystem.mail.quickview.components.QuickFaceLook
import com.otus.designsystem.mail.quickview.components.ScrollingFaces
import com.otus.designsystem.shared.pages.components.PageNote
import com.otus.designsystem.shared.rows.components.LocalEntriesOnRaised
import com.otus.designsystem.shared.rows.elements.SectionCaption
import com.otus.designsystem.theme.tokens.overlays.SheetTokens
import com.otus.designsystem.theme.tokens.rows.EntryTokens
import com.otus.designsystem.theme.tokens.rows.InboxTokens
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.looksLikeAddress
import com.otus.mail.feature.inbox.domain.searchMessages
import com.otus.mail.feature.inbox.domain.shownName
import com.otus.mail.feature.inbox.presentation.conversationRows

/**
 * What a typed search finds: the people whose name or address matches in a row of faces, which
 * stays put, and every hit under it as the list shows them.
 */
@Composable
internal fun SearchMatches(
    room: SearchRoom,
    modifier: Modifier,
) {
    val state = room.state
    val sources = room.sources
    val applied = sources.filters.applied
    val query = state.search.query
    val hits =
        remember(
            sources.messages,
            state.matching,
            applied,
        ) { searchMessages(sources.messages, state.matching, applied) }
    val people = remember(hits, query) { matchingPeople(hits, query, limit = Int.MAX_VALUE) }
    Column(modifier) {
        if (people.isNotEmpty()) SearchPeople(room, people, byAddress = looksLikeAddress(query))
        SectionCaption("Mail", Modifier.padding(horizontal = SheetTokens.inset))
        SearchMail(room, hits, Modifier.weight(1f))
    }
}

/**
 * The matching [people] as faces in a row that scrolls, counting the ones still to the right.
 * Each face is named by its address while the query is one [byAddress], else by first name, and
 * shows that person's mail.
 */
@Composable
private fun SearchPeople(
    room: SearchRoom,
    people: List<MailSender>,
    byAddress: Boolean,
) {
    val state = room.state
    SectionCaption("People", Modifier.padding(horizontal = SheetTokens.inset))
    ScrollingFaces(
        people.map { person ->
            QuickFace(
                if (byAddress) person.address else person.firstName,
                "Mail from ${person.shownName}",
                QuickFaceLook.Person(person.chipAvatar),
                {
                    state.update(state.search.copy(query = "", senders = setOf(person.address)))
                    state.showResults()
                },
            )
        },
        Modifier.padding(horizontal = InboxTokens.pageInset),
    )
}

/** Every hit as the list draws it, on the page's color so the rows stand out from the raised panel. */
@Composable
private fun SearchMail(
    room: SearchRoom,
    hits: List<MailMessage>,
    modifier: Modifier,
) {
    val today = currentLocalDate()
    val look = room.sources.page.rowLook
    val rows =
        remember(hits, look, room.dateFormatter, today) { conversationRows(hits, look, room.dateFormatter, today) }
    CompositionLocalProvider(LocalEntriesOnRaised provides true) {
        LazyColumn(
            modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = InboxTokens.pageInset),
            verticalArrangement = Arrangement.spacedBy(EntryTokens.gap),
        ) {
            if (hits.isEmpty()) item(key = EMPTY_KEY) { PageNote("No matching mail") }
            items(hits, key = { it.id }) { message ->
                rows[message.id]?.let { ConversationRow(message, it) { room.gestures.tap(message) } }
            }
        }
    }
}

private const val EMPTY_KEY = "empty"
