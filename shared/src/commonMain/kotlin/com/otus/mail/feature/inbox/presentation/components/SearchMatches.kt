package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.otus.dateformat.currentLocalDate
import com.otus.designsystem.mail.quickview.components.QuickFace
import com.otus.designsystem.mail.quickview.components.QuickFaceLook
import com.otus.designsystem.mail.quickview.components.ScrollingFaces
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_search
import com.otus.designsystem.shared.actionbar.components.ActionBar
import com.otus.designsystem.shared.actionbar.components.BarAction
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
import com.otus.mail.feature.inbox.presentation.FilterCount
import com.otus.mail.feature.inbox.presentation.InboxState
import com.otus.mail.feature.inbox.presentation.conversationRows
import com.otus.mail.feature.inbox.presentation.showLabel

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

/**
 * Unless the keyboard is up, the bar under a typed search: Show and how many of the [listed] mail
 * match, which puts them in the list. It comes as soon as the keyboard starts to close, riding on
 * its edge, so it is in place the moment the keyboard is gone.
 */
@Composable
internal fun SearchResultsBar(
    listed: ListedMail,
    state: InboxState,
) {
    if (keyboardHolds()) return
    val count =
        remember(listed, state.matching) { searchMessages(listed.messages, state.matching, listed.filters).size }
    ActionBar(
        BarAction(
            FilterCount.Known(count.toLong()).showLabel,
            Res.drawable.symbol_search,
            enabled = count > 0,
            onClick = state::showResults,
        ),
    )
}

/** Whether the keyboard is up or rising, rather than down or closing. */
@Composable
private fun keyboardHolds(): Boolean {
    val keyboard = WindowInsets.ime.getBottom(LocalDensity.current)
    val last = remember { IntArray(1) }
    val holds = keyboard > 0 && keyboard >= last[0]
    SideEffect { last[0] = keyboard }
    return holds
}

private const val EMPTY_KEY = "empty"
