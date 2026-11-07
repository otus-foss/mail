package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.otus.mail.feature.contacts.domain.BookKey
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.domain.MergePlan
import com.otus.mail.feature.contacts.domain.PossibleDuplicate
import com.otus.mail.feature.contacts.presentation.ContactsState

/**
 * What the Contacts page itself tracks: the search, the books it shows, the selection, and which
 * sheet is open. The contacts live in [ContactsState].
 */
internal class ContactsView(
    queryState: MutableState<String>,
) {
    /** The search text, kept across recreation through [rememberContactsView]. */
    var query by queryState

    /** The books the list shows; none means every book. */
    var books by mutableStateOf(emptySet<BookKey>())

    /** Whether the full book filter panel is open. */
    var filtering by mutableStateOf(false)

    /** The header's height, so the filter panel can start from it. */
    var panelHeight by mutableStateOf(0.dp)

    /** The main cards of the selected people. */
    var selected by mutableStateOf(emptySet<CardKey>())

    /** The merge the merge sheet shows, or null while it is closed. */
    var merging by mutableStateOf<MergePlan?>(null)

    /** Whether the New group sheet is open. */
    var naming by mutableStateOf(false)

    /** Whether the delete dialog is open. */
    var deleting by mutableStateOf(false)

    /** Whether the list shows everything: no search and no book filter. */
    val showsAll: Boolean get() = query.isBlank() && books.isEmpty()

    fun clearSelection() {
        selected = emptySet()
    }

    /** Selects the person with main card [key], or unselects them when they were selected. */
    fun toggle(key: CardKey) {
        selected = selected.toggled(key)
    }
}

/** A [ContactsView] whose search text survives recreation; everything else starts fresh. */
@Composable
internal fun rememberContactsView(): ContactsView {
    val query = rememberSaveable { mutableStateOf("") }
    return remember(query) { ContactsView(query) }
}

/** Whether a card in [cardBooks] shows while these books are picked; none picked shows every card. */
internal fun Set<BookKey>.admits(cardBooks: List<BookKey>): Boolean = isEmpty() || cardBooks.any { it in this }

/** The people with a card in one of [books]. */
internal fun ContactsState.peopleIn(books: Set<BookKey>): List<ContactPerson> =
    people.filter { person -> person.cards.any { books.admits(it.books) } }

/** How [cards] would merge into one of the books the user can write to, or null when they cannot. */
internal fun ContactsState.mergePlan(cards: List<ContactCard>): MergePlan? = MergePlan.of(cards, writableBooks)

/** How the two cards of [duplicate] would merge. */
internal fun ContactsState.mergePlan(duplicate: PossibleDuplicate): MergePlan? =
    mergePlan(listOf(duplicate.first, duplicate.second))

/** Opens the file picker for an import, or null when there is nowhere to import to. */
internal fun ContactsState.importAction(): (() -> Unit)? =
    this::pickImport.takeIf { canTransfer && defaultBook != null }
