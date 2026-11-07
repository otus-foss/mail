package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_group_add
import com.otus.designsystem.resources.symbol_person_add
import com.otus.designsystem.shared.contacts.components.ContactRow
import com.otus.designsystem.shared.controls.elements.ActionButton
import com.otus.designsystem.shared.controls.elements.PlainAction
import com.otus.designsystem.shared.overlays.components.SheetFrame
import com.otus.designsystem.shared.overlays.elements.sheetInset
import com.otus.designsystem.shared.rows.elements.SectionCaption
import com.otus.designsystem.shared.selection.components.PickerSearch
import com.otus.designsystem.theme.colors.OtusColors
import com.otus.designsystem.theme.tokens.layout.PageTokens
import com.otus.designsystem.theme.tokens.overlays.SheetTokens
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactAddress
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactDraft
import com.otus.mail.feature.contacts.domain.DraftValue
import com.otus.mail.feature.contacts.presentation.ContactsState
import com.otus.mail.feature.contacts.presentation.addAddress
import com.otus.mail.feature.contacts.presentation.avatar
import com.otus.mail.feature.contacts.presentation.book
import com.otus.mail.feature.contacts.presentation.withAddress

/**
 * What the app offers for a person a message names: their card when the address is saved already;
 * else a card with the same name to add the address to, a new contact, or any other card. People
 * are never merged by name alone; the user picks.
 */
@Composable
internal fun PersonContactSheet(
    person: ContactAddress,
    state: ContactsState,
    onNew: () -> Unit,
    onOpen: (CardKey) -> Unit,
    onDismiss: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val name = person.name.ifBlank { person.address }
    val saved = state.withAddress(person.address)
    val writable = state.cards.filter { card -> !card.isGroup && card.books.any { state.book(it)?.canWrite == true } }
    val sameName = writable.filter { it.displayName.equals(person.name.trim(), ignoreCase = true) && it !in saved }
    val add = { card: ContactCard ->
        state.addAddress(card, person.address)
        state.notice = "Added to contact"
        onDismiss()
    }
    SheetFrame(name, onDismiss, done = null, closeButton = true) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            Text(person.address, Modifier.sheetInset(), color = OtusColors.text2)
            val create = {
                state.prefill = newDraft(person)
                onNew()
            }
            val pick = { picking = true }.takeIf { writable.isNotEmpty() }
            when {
                picking -> PickContact(writable, query, { query = it }, add)
                saved.isNotEmpty() -> SavedContacts(saved, onOpen)
                else -> NewContactOptions(sameName.firstOrNull(), onOpen, add, create, pick)
            }
        }
    }
}

@Composable
private fun PickContact(
    contacts: List<ContactCard>,
    query: String,
    onQuery: (String) -> Unit,
    onAdd: (ContactCard) -> Unit,
) {
    PickerSearch(query, onQuery, "Search people…", Modifier.sheetInset())
    contacts
        .filter { query.isBlank() || it.displayName.contains(query, ignoreCase = true) }
        .take(PICKER_LIMIT)
        .forEach { card -> CardRow(card) { onAdd(card) } }
}

@Composable
private fun SavedContacts(
    contacts: List<ContactCard>,
    onOpen: (CardKey) -> Unit,
) {
    SectionCaption("In your contacts", RowInset)
    contacts.forEach { card -> CardRow(card) { onOpen(card.key) } }
}

@Composable
private fun NewContactOptions(
    match: ContactCard?,
    onOpen: (CardKey) -> Unit,
    onAdd: (ContactCard) -> Unit,
    onNew: () -> Unit,
    onPick: (() -> Unit)?,
) {
    if (match != null) {
        SectionCaption("Maybe already in your contacts", RowInset)
        CardRow(match) { onOpen(match.key) }
        ActionButton("Add this address there", { onAdd(match) }, Modifier.sheetInset().fillMaxWidth(), primary = true)
    }
    PlainAction("New contact", Res.drawable.symbol_person_add, onNew, Modifier.fillMaxWidth())
    if (onPick != null) {
        PlainAction("Add to another contact…", Res.drawable.symbol_group_add, onPick, Modifier.fillMaxWidth())
    }
}

@Composable
private fun CardRow(
    card: ContactCard,
    onClick: () -> Unit,
) {
    ContactRow(card.displayName, card.emails.firstOrNull()?.value ?: "No address", card.avatar, onClick, RowInset)
}

/** A draft named and addressed as the message names [person]. */
private fun newDraft(person: ContactAddress): ContactDraft {
    val words =
        person.name
            .trim()
            .split(' ')
            .filter { it.isNotEmpty() }
    return ContactDraft(
        given = words.dropLast(1).joinToString(" ").ifEmpty { words.firstOrNull().orEmpty() },
        surname = if (words.size > 1) words.last() else "",
        emails = listOf(DraftValue(value = person.address)),
    )
}

private const val PICKER_LIMIT = 50

// Captions and contact rows pad their own text by the page's text inset, so they add only the rest.
private val RowInset = Modifier.padding(horizontal = SheetTokens.inset - PageTokens.textInset)
