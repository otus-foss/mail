package com.otusfoss.mail.feature.contacts.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_group_add
import com.otusfoss.core.designsystem.resources.symbol_person
import com.otusfoss.core.designsystem.resources.symbol_person_add
import com.otusfoss.core.designsystem.shared.contacts.components.ContactRow
import com.otusfoss.core.designsystem.shared.controls.elements.ActionButton
import com.otusfoss.core.designsystem.shared.controls.elements.PlainAction
import com.otusfoss.core.designsystem.shared.overlays.components.SheetFrame
import com.otusfoss.core.designsystem.shared.overlays.elements.sheetInset
import com.otusfoss.core.designsystem.shared.people.components.ChipAvatar
import com.otusfoss.core.designsystem.shared.people.components.PersonAvatar
import com.otusfoss.core.designsystem.shared.rows.elements.SectionCaption
import com.otusfoss.core.designsystem.shared.selection.components.PickerSearch
import com.otusfoss.core.designsystem.theme.colors.OtusColors
import com.otusfoss.core.designsystem.theme.tokens.layout.PageTokens
import com.otusfoss.core.designsystem.theme.tokens.overlays.SheetTokens
import com.otusfoss.core.designsystem.theme.tokens.people.AvatarTokens
import com.otusfoss.core.designsystem.theme.tokens.rows.StripTokens
import com.otusfoss.mail.feature.contacts.domain.CardKey
import com.otusfoss.mail.feature.contacts.domain.ContactAddress
import com.otusfoss.mail.feature.contacts.domain.ContactCard
import com.otusfoss.mail.feature.contacts.domain.ContactDraft
import com.otusfoss.mail.feature.contacts.domain.DraftValue
import com.otusfoss.mail.feature.contacts.presentation.ContactsState
import com.otusfoss.mail.feature.contacts.presentation.addAddress
import com.otusfoss.mail.feature.contacts.presentation.avatar
import com.otusfoss.mail.feature.contacts.presentation.book
import com.otusfoss.mail.feature.contacts.presentation.withAddress

/**
 * What the app offers for a person a message names: their card when the address is saved already;
 * else a card with the same name to add the address to, a new contact, or any other card. People
 * are never merged by name alone; the user picks.
 */
@Composable
internal fun PersonContactSheet(
    person: ContactAddress,
    avatar: ChipAvatar,
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
    SheetFrame(
        name,
        onDismiss,
        done = null,
        closeButton = true,
        titleLeading = { PersonAvatar(avatar, AvatarTokens.menuSize) },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            // The extra gap sets the name and address apart from the choices below them.
            Text(person.address, Modifier.sheetInset().padding(bottom = SheetTokens.gap), color = OtusColors.text2)
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
    // The header already shows who this is, so the sheet only offers to open the card.
    Actions {
        contacts.forEach { card ->
            val label = if (contacts.size == 1) "Open contact" else "Open ${card.displayName}"
            PlainAction(label, Res.drawable.symbol_person, { onOpen(card.key) }, ActionInset)
        }
    }
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
    Actions {
        PlainAction("New contact", Res.drawable.symbol_person_add, onNew, ActionInset)
        if (onPick != null) {
            PlainAction("Add to another contact…", Res.drawable.symbol_group_add, onPick, ActionInset)
        }
    }
}

/** Stacks plain actions on their touch targets, which already space them apart. */
@Composable
private fun Actions(content: @Composable ColumnScope.() -> Unit) {
    Column(content = content)
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

// Plain actions pad their own symbol, so the symbol lines up with the title and address.
private val ActionInset =
    Modifier
        .fillMaxWidth()
        .padding(horizontal = SheetTokens.inset - StripTokens.plainPadding)
