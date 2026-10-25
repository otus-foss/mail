package com.otus.mail.feature.contacts.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.otus.designsystem.components.ActionBarHost
import com.otus.designsystem.components.ConfirmDialog
import com.otus.designsystem.components.rememberLetterIndexState
import com.otus.designsystem.components.InputSheet
import com.otus.designsystem.components.LetterIndex
import com.otus.designsystem.theme.PageTokens
import com.otus.designsystem.theme.panelTextStyle
import com.otus.mail.feature.contacts.domain.BookKey
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.domain.MergePlan
import com.otus.mail.feature.contacts.presentation.ContactsState
import com.otus.mail.feature.contacts.presentation.components.ContactList
import com.otus.mail.feature.contacts.presentation.components.ContactListActions
import com.otus.mail.feature.contacts.presentation.components.ContactListContent
import com.otus.mail.feature.contacts.presentation.components.ContactsEmpty
import com.otus.mail.feature.contacts.presentation.components.ContactsFilterPanel
import com.otus.mail.feature.contacts.presentation.components.ContactsListBar
import com.otus.mail.feature.contacts.presentation.components.ContactsPanel
import com.otus.mail.feature.contacts.presentation.components.ContactsSelectionBar
import com.otus.mail.feature.contacts.presentation.components.ImportSheet
import com.otus.mail.feature.contacts.presentation.components.ListBarActions
import com.otus.mail.feature.contacts.presentation.components.MergeSheet
import com.otus.mail.feature.contacts.presentation.components.SelectionBarActions
import com.otus.mail.feature.contacts.presentation.components.emptyKind
import com.otus.mail.feature.inbox.domain.MailSender

/** Where the Contacts page leads. */
internal class ContactsNavigation(
    val onOpenNavigation: () -> Unit,
    val onOpen: (CardKey) -> Unit,
    val onNew: () -> Unit,
    val onWrite: (List<MailSender>) -> Unit,
)

/**
 * Every contact: search and address-book chips on top, groups and people below, and a bar that
 * adds a contact or, while people are selected, acts on them. Merge and import open as sheets.
 */
@Composable
internal fun ContactsScreen(
    state: ContactsState,
    navigation: ContactsNavigation,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // The books the list shows; none means every book.
    var books by remember { mutableStateOf(emptySet<BookKey>()) }
    var filtering by remember { mutableStateOf(false) }
    var panelHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    var selected by remember { mutableStateOf(emptySet<CardKey>()) }
    val letterIndex = rememberLetterIndexState()
    var merging by remember { mutableStateOf<MergePlan?>(null) }
    var naming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val inBooks = { keys: Set<BookKey>, cardBooks: List<BookKey> -> keys.isEmpty() || cardBooks.any { it in keys } }
    val shown = state.people.filter { person -> person.cards.any { inBooks(books, it.books) } }
    val people = if (query.isBlank()) shown else shown.filter { it.matches(query) }
    val chosen = state.people.filter { it.main.key in selected }
    val chosenCards = chosen.flatMap { it.cards }
    val duplicates = state.duplicates
    val planFor = { cards: List<ContactCard> -> MergePlan.of(cards, state.writableBooks) }
    val clear = { selected = emptySet() }
    ProvideTextStyle(panelTextStyle()) {
        LetterIndex(letterIndex, Modifier.fillMaxSize()) {
        ActionBarHost(
            bar = {
                if (selected.isEmpty()) {
                    ContactsListBar(
                        ListBarActions(
                            navigation.onNew,
                            onNewGroup = { naming = true }.takeIf { state.defaultBook != null },
                            onFindDuplicates =
                                duplicates.firstOrNull()?.let {
                                    {
                                        merging =
                                            planFor(listOf(it.first, it.second))
                                    }
                                },
                            duplicates = duplicates.size,
                            onImport = state::pickImport.takeIf { state.canTransfer && state.defaultBook != null },
                            onExport =
                                { state.export(state.cards.filterNot { it.isGroup }) }.takeIf {
                                    state.canTransfer &&
                                        state.cards.isNotEmpty()
                                },
                        ),
                    )
                } else {
                    val pair = chosenCards.takeIf { it.size == 2 }
                    val sameAddress =
                        pair != null &&
                            duplicates.any { setOf(it.first.key, it.second.key) == pair.map { c -> c.key }.toSet() }
                    ContactsSelectionBar(
                        chosen,
                        SelectionBarActions(
                            onWrite = {
                                navigation.onWrite(
                                    chosen.mapNotNull { p ->
                                        p.emails.firstOrNull()?.let { MailSender(p.name, it.value) }
                                    },
                                )
                                clear()
                            },
                            onMerge =
                                chosenCards
                                    .takeIf { it.size >= 2 && chosen.size == selected.size }
                                    ?.let { planFor(it) }
                                    ?.let { plan -> { merging = plan } },
                            sharedAddress = sameAddress,
                            groups =
                                state.groups.filter { group ->
                                    state.book(group.books.first())?.canWrite == true
                                },
                            onAddToGroup = { group ->
                                state.addToGroup(group, chosenCards)
                                clear()
                            },
                            onNewGroup = { naming = true },
                            onExport = { state.export(chosenCards) },
                            onDelete =
                                { deleting = true }.takeIf {
                                    chosenCards.all { card ->
                                        card.books.all {
                                            state.book(it)?.canWrite ==
                                                true
                                        }
                                    }
                                },
                        ),
                    )
                }
            },
            Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
        ) { barPadding ->
            Column(Modifier.fillMaxSize().padding(horizontal = PageTokens.pageInset)) {
                ContactsPanel(
                    query,
                    { query = it },
                    state.books,
                    books,
                    { books = it },
                    { filtering = true },
                    selected.size,
                    navigation.onOpenNavigation,
                    clear,
                    Modifier
                        .padding(top = PageTokens.pageInset)
                        .onSizeChanged { panelHeight = with(density) { it.height.toDp() } },
                )
                Box(Modifier.weight(1f)) {
                    val content =
                        ContactListContent(
                            people,
                            state.groups.filter { g -> query.isBlank() && inBooks(books, g.books) },
                            duplicates.firstOrNull()?.takeIf { books.isEmpty() && query.isBlank() },
                            duplicates.size,
                            state.waiting,
                            books.singleOrNull()?.let(state::book)?.takeIf { !it.canWrite },
                            booksOf = { card -> card.books.mapNotNull(state::book) },
                            membersOf = { group -> state.members(group).size },
                        )
                    val empty = emptyKind(state, people.isEmpty() && content.groups.isEmpty(), query.isNotBlank())
                    if (empty != null) {
                        ContactsEmpty(
                            empty,
                            state::pickImport.takeIf { state.canTransfer && state.defaultBook != null },
                        )
                    } else {
                        ContactList(
                            content,
                            ContactListActions(
                                selected,
                                navigation.onOpen,
                                onToggle = { key ->
                                    selected =
                                        if (key in
                                            selected
                                        ) {
                                            selected - key
                                        } else {
                                            selected + key
                                        }
                                },
                                onReviewDuplicate = {
                                    content.duplicate?.let {
                                        merging =
                                            planFor(listOf(it.first, it.second))
                                    }
                                },
                            ),
                            barPadding,
                            letterIndex,
                        )
                    }
                }
            }
        }
        }
    }
    ContactsFilterPanel(
        filtering,
        state.books,
        books,
        countOf = { keys -> state.people.count { person -> person.cards.any { inBooks(keys, it.books) } } },
        collapsedHeight = panelHeight,
        onApply = {
            books = it
            filtering = false
        },
        onDismiss = { filtering = false },
    )
    merging?.let { plan ->
        MergeSheet(plan, state.writableBooks, onChange = { merging = it }, onShowAsOne = {
            state.showAsOne(plan.cards.map { it.key }.toSet())
            merging = null
            clear()
        }, onMerge = {
            state.merge(it)
            merging = null
            clear()
        }, onDismiss = { merging = null })
    }
    state.importing?.let { file ->
        ImportSheet(file, state.writableBooks, state.cards, state::import) { state.importing = null }
    }
    if (naming) {
        InputSheet(
            title = "New group",
            label = "Name",
            confirmLabel = "Create",
            onConfirm = { name ->
                state.defaultBook?.let { state.createGroup(name, it.key, chosenCards) }
                naming = false
                clear()
            },
            onDismiss = { naming = false },
        )
    }
    if (deleting) {
        DeleteDialog(chosen, onDelete = {
            state.delete(chosenCards.map { it.key })
            deleting = false
            clear()
        }) { deleting = false }
    }
}

@Composable
private fun DeleteDialog(
    chosen: List<ContactPerson>,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = chosen.singleOrNull()?.let { "Delete “${it.name}”?" } ?: "Delete ${chosen.size} contacts?"
    ConfirmDialog(
        title = title,
        message =
            if (chosen.size == 1) {
                "It’s removed from your server, so every app loses it."
            } else {
                "They’re removed from your server, so every app loses them."
            },
        confirmLabel = "Delete",
        onConfirm = onDelete,
        onDismiss = onDismiss,
    )
}
