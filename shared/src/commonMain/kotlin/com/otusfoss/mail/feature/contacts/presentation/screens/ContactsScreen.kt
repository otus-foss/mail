package com.otusfoss.mail.feature.contacts.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.otusfoss.core.designsystem.shared.actionbar.components.ActionBarHost
import com.otusfoss.core.designsystem.shared.contacts.components.LetterIndex
import com.otusfoss.core.designsystem.shared.contacts.components.LetterIndexSide
import com.otusfoss.core.designsystem.shared.contacts.components.LetterIndexState
import com.otusfoss.core.designsystem.shared.contacts.components.rememberLetterIndexState
import com.otusfoss.core.designsystem.shared.pages.components.belowControlPanel
import com.otusfoss.core.designsystem.shared.pages.components.pageSideInset
import com.otusfoss.core.designsystem.theme.typography.panelTextStyle
import com.otusfoss.mail.feature.contacts.domain.CardKey
import com.otusfoss.mail.feature.contacts.presentation.ContactsNavigation
import com.otusfoss.mail.feature.contacts.presentation.ContactsState
import com.otusfoss.mail.feature.contacts.presentation.book
import com.otusfoss.mail.feature.contacts.presentation.components.BookFilter
import com.otusfoss.mail.feature.contacts.presentation.components.ContactList
import com.otusfoss.mail.feature.contacts.presentation.components.ContactListActions
import com.otusfoss.mail.feature.contacts.presentation.components.ContactListContent
import com.otusfoss.mail.feature.contacts.presentation.components.ContactsEmpty
import com.otusfoss.mail.feature.contacts.presentation.components.ContactsPageBar
import com.otusfoss.mail.feature.contacts.presentation.components.ContactsPanel
import com.otusfoss.mail.feature.contacts.presentation.components.ContactsSheets
import com.otusfoss.mail.feature.contacts.presentation.components.ContactsView
import com.otusfoss.mail.feature.contacts.presentation.components.ListNotices
import com.otusfoss.mail.feature.contacts.presentation.components.admits
import com.otusfoss.mail.feature.contacts.presentation.components.emptyKind
import com.otusfoss.mail.feature.contacts.presentation.components.importAction
import com.otusfoss.mail.feature.contacts.presentation.components.mergePlan
import com.otusfoss.mail.feature.contacts.presentation.components.peopleIn
import com.otusfoss.mail.feature.contacts.presentation.components.rememberContactsView
import com.otusfoss.mail.feature.contacts.presentation.members

/**
 * Every contact: search and address-book chips on top, groups and people below, and a bar that
 * adds a contact or, while people are selected, acts on them. Merge and import open as sheets.
 */
@Composable
internal fun ContactsScreen(
    state: ContactsState,
    navigation: ContactsNavigation,
    letterSide: LetterIndexSide,
    onLetterSideChange: (LetterIndexSide) -> Unit,
) {
    val view = rememberContactsView()
    val letterIndex = rememberLetterIndexState()
    val chosen = state.people.filter { it.main.key in view.selected }
    ProvideTextStyle(panelTextStyle()) {
        LetterIndex(letterIndex, letterSide, onLetterSideChange, Modifier.fillMaxSize()) {
            ActionBarHost(
                bar = { ContactsPageBar(state, view, chosen, navigation) },
                Modifier.fillMaxSize().belowControlPanel(),
            ) { barPadding ->
                Column(Modifier.fillMaxSize()) {
                    ContactsHeader(state, view, navigation.onOpenNavigation)
                    Box(Modifier.weight(1f).pageSideInset()) {
                        ContactsBody(state, view, navigation.onOpen, barPadding, letterIndex)
                    }
                }
            }
        }
    }
    ContactsSheets(state, view, chosen)
}

/** The header, which reports its height so the filter panel can grow from it. */
@Composable
private fun ContactsHeader(
    state: ContactsState,
    view: ContactsView,
    onOpenNavigation: () -> Unit,
) {
    val density = LocalDensity.current
    ContactsPanel(
        view.query,
        { view.query = it },
        BookFilter(state.books, view.books, { view.books = it }, { view.filtering = true }),
        view.selected.size,
        onOpenNavigation,
        view::clearSelection,
        Modifier.onSizeChanged { view.panelHeight = with(density) { it.height.toDp() } },
    )
}

/** The list of groups and people the search and books let through, or why it is empty. */
@Composable
private fun ContactsBody(
    state: ContactsState,
    view: ContactsView,
    onOpen: (CardKey) -> Unit,
    barPadding: PaddingValues,
    letterIndex: LetterIndexState,
) {
    val content = state.listContent(view)
    val empty = emptyKind(state, content.people.isEmpty() && content.groups.isEmpty(), view.query.isNotBlank())
    if (empty != null) {
        ContactsEmpty(empty, state.importAction())
    } else {
        ContactList(
            content,
            ContactListActions(
                view.selected,
                onOpen,
                onToggle = view::toggle,
                onReviewDuplicate = {
                    val duplicate = content.notices.duplicate
                    if (duplicate != null) view.merging = state.mergePlan(duplicate)
                },
            ),
            barPadding,
            letterIndex,
        )
    }
}

/** What the list shows for the current search, books, and selection. */
private fun ContactsState.listContent(view: ContactsView): ContactListContent =
    ContactListContent(
        peopleIn(view.books).filter { view.query.isBlank() || it.matches(view.query) },
        groups.filter { g -> view.query.isBlank() && view.books.admits(g.books) },
        ListNotices(
            view.books
                .singleOrNull()
                ?.let(this::book)
                ?.takeIf { !it.canWrite },
            waiting,
            duplicates.firstOrNull()?.takeIf { view.showsAll },
            duplicates.size,
        ),
        booksOf = { card -> card.books.mapNotNull(this::book) },
        membersOf = { group -> members(group).size },
        // Only the full list features them, not a search, a filter, or a selection.
        frequent = mostFrequent.takeIf { view.showsAll && view.selected.isEmpty() }.orEmpty(),
        ownAddresses = ownAddresses(),
    )
