package com.otusfoss.mail.feature.contacts.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_chevron_left
import com.otusfoss.core.designsystem.resources.symbol_group_add
import com.otusfoss.core.designsystem.resources.symbol_person
import com.otusfoss.core.designsystem.resources.symbol_person_add
import com.otusfoss.core.designsystem.shared.contacts.components.ContactRow
import com.otusfoss.core.designsystem.shared.controls.elements.ActionButton
import com.otusfoss.core.designsystem.shared.controls.elements.IconButton
import com.otusfoss.core.designsystem.shared.controls.elements.IconButtonLook
import com.otusfoss.core.designsystem.shared.controls.elements.PlainAction
import com.otusfoss.core.designsystem.shared.controls.elements.Symbol
import com.otusfoss.core.designsystem.shared.overlays.components.SheetFrame
import com.otusfoss.core.designsystem.shared.overlays.elements.sheetInset
import com.otusfoss.core.designsystem.shared.people.components.ChipAvatar
import com.otusfoss.core.designsystem.shared.people.components.PersonAvatar
import com.otusfoss.core.designsystem.shared.rows.elements.SectionCaption
import com.otusfoss.core.designsystem.shared.selection.components.PickerSearch
import com.otusfoss.core.designsystem.shared.settings.components.SettingPageRow
import com.otusfoss.core.designsystem.theme.tokens.layout.PageTokens
import com.otusfoss.core.designsystem.theme.tokens.overlays.SheetTokens
import com.otusfoss.core.designsystem.theme.tokens.people.AvatarTokens
import com.otusfoss.core.designsystem.theme.tokens.rows.StripTokens
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.presentation.components.firstName
import com.otusfoss.mail.feature.contacts.domain.CardKey
import com.otusfoss.mail.feature.contacts.domain.ContactAddress
import com.otusfoss.mail.feature.contacts.domain.ContactCard
import com.otusfoss.mail.feature.contacts.domain.ContactDraft
import com.otusfoss.mail.feature.contacts.domain.DraftValue
import com.otusfoss.mail.feature.contacts.domain.firstNote
import com.otusfoss.mail.feature.contacts.presentation.ContactsState
import com.otusfoss.mail.feature.contacts.presentation.PersonMail
import com.otusfoss.mail.feature.contacts.presentation.addAddress
import com.otusfoss.mail.feature.contacts.presentation.avatar
import com.otusfoss.mail.feature.contacts.presentation.book
import com.otusfoss.mail.feature.contacts.presentation.withAddress

/**
 * The sheet about a person a message names: their card's note, ways to write, call, and see their
 * mail, the people they share threads with, and their recent threads. A saved person's card opens
 * from it; for anyone else it offers a card with the same name to add the address to, a new
 * contact, or any other card. People are never merged by name alone; the user picks.
 */
@Composable
internal fun PersonContactSheet(
    person: ContactAddress,
    avatar: ChipAvatar,
    state: ContactsState,
    mail: PersonMail,
    onNew: () -> Unit,
    onOpen: (CardKey) -> Unit,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf(SheetPage.Overview) }
    val activity = rememberPersonActivity(mail)
    val shared = activity?.history?.shared.orEmpty()
    val name = person.name.ifBlank { person.address }
    val back = { page = SheetPage.Overview }
    val add = { card: ContactCard ->
        state.addAddress(card, person.address)
        state.notice = "Added to contact"
        onDismiss()
    }
    val onShared = page == SheetPage.Shared
    SheetFrame(
        if (onShared) "Threads with ${MailSender(person.name, person.address).firstName}" else name,
        onDismiss,
        done = null,
        closeButton = true,
        titleLeading = {
            if (onShared) {
                IconButton("Back", back, look = IconButtonLook.Plain) {
                    Symbol(Res.drawable.symbol_chevron_left, mirrorInRtl = true)
                }
            } else {
                PersonAvatar(avatar, AvatarTokens.size)
            }
        },
        subtitle = if (onShared) alsoTakePart(shared.size) else person.address,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = SheetTokens.gap),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            when (page) {
                SheetPage.Overview -> {
                    val pages = PageLinks(onPick = { page = SheetPage.Picking }, onShared = { page = SheetPage.Shared })
                    PersonPage(person, state, mail, activity, ContactLinks(onNew, onOpen, add), pages)
                }

                SheetPage.Shared -> {
                    SharedThreads(shared, mail.showWith)
                }

                SheetPage.Picking -> {
                    PickContact(state, add)
                }
            }
        }
    }
}

/** Which of the sheet's pages shows; the others swap in over the overview rather than stack a sheet. */
private enum class SheetPage { Overview, Shared, Picking }

/** Where the sheet's contact actions lead: a [new] contact, a saved card to [open], or one to [add] the address to. */
private class ContactLinks(
    val new: () -> Unit,
    val open: (CardKey) -> Unit,
    val add: (ContactCard) -> Unit,
)

/** The sheet's other pages: the cards to [onPick] from, and the people [onShared] with them. */
private class PageLinks(
    val onPick: () -> Unit,
    val onShared: () -> Unit,
)

@Composable
private fun PersonPage(
    person: ContactAddress,
    state: ContactsState,
    mail: PersonMail,
    activity: PersonActivity?,
    contacts: ContactLinks,
    pages: PageLinks,
) {
    val saved = state.withAddress(person.address)
    val card = saved.firstOrNull()
    PersonOverview(
        note = card?.let { listOfNotNull(it.role, it.card.firstNote).joinToString("\n") },
        phone = saved.firstNotNullOfOrNull { it.phones.firstOrNull()?.value },
        mail = mail,
        activity = activity,
        onShared = pages.onShared,
    )
    if (saved.isNotEmpty()) {
        SavedContacts(saved, contacts.open)
        return
    }
    val writable = state.writableCards()
    val create = {
        state.prefill = newDraft(person)
        contacts.new()
    }
    NewContactOptions(
        writable.firstOrNull { it.displayName.equals(person.name.trim(), ignoreCase = true) },
        contacts.open,
        contacts.add,
        create,
        pages.onPick.takeIf { writable.isNotEmpty() },
    )
}

@Composable
private fun PickContact(
    state: ContactsState,
    onAdd: (ContactCard) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    PickerSearch(query, { query = it }, "Search people…", Modifier.sheetInset())
    state
        .writableCards()
        .filter { query.isBlank() || it.displayName.contains(query, ignoreCase = true) }
        .take(PICKER_LIMIT)
        .forEach { card -> CardRow(card) { onAdd(card) } }
}

private fun ContactsState.writableCards(): List<ContactCard> =
    cards.filter { card -> !card.isGroup && card.books.any { book(it)?.canWrite == true } }

@Composable
private fun SavedContacts(
    contacts: List<ContactCard>,
    onOpen: (CardKey) -> Unit,
) {
    contacts.forEach { card ->
        val label = if (contacts.size == 1) "Open contact" else "Open ${card.displayName}"
        SettingPageRow(label, { onOpen(card.key) }, RowInset, icon = Res.drawable.symbol_person)
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
internal val RowInset = Modifier.padding(horizontal = SheetTokens.inset - PageTokens.textInset)

// Plain actions pad their own symbol, so the symbol lines up with the title and address.
private val ActionInset =
    Modifier
        .fillMaxWidth()
        .padding(horizontal = SheetTokens.inset - StripTokens.plainPadding)
