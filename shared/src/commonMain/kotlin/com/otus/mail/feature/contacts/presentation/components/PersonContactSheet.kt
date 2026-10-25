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
import com.otus.designsystem.components.ClosableSheet
import com.otus.designsystem.components.ContactRow
import com.otus.designsystem.components.PickerSearch
import com.otus.designsystem.elements.ActionButton
import com.otus.designsystem.elements.PlainAction
import com.otus.designsystem.elements.SectionCaption
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_group_add
import com.otus.designsystem.resources.symbol_person_add
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.SheetTokens
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactDraft
import com.otus.mail.feature.contacts.domain.DraftValue
import com.otus.mail.feature.contacts.presentation.ContactsState
import com.otus.mail.feature.contacts.presentation.addAddress
import com.otus.mail.feature.contacts.presentation.avatar
import com.otus.mail.feature.contacts.presentation.withAddress
import com.otus.mail.feature.inbox.domain.MailSender

/**
 * What the app offers for a person a message names: their card when the address is saved already;
 * else a card with the same name to add the address to, a new contact, or any other card. People
 * are never merged by name alone; the user picks.
 */
@Composable
internal fun PersonContactSheet(
    person: MailSender,
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
    ClosableSheet(name, onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            Text(person.address, Inset, color = OtusColors.text2)
            when {
                picking -> {
                    PickerSearch(query, { query = it }, "Search people…", Inset)
                    writable
                        .filter { query.isBlank() || it.displayName.contains(query, ignoreCase = true) }
                        .take(PICKER_LIMIT)
                        .forEach { card -> CardRow(card) { add(card) } }
                }

                saved.isNotEmpty() -> {
                    SectionCaption("In your contacts")
                    saved.forEach { card -> CardRow(card) { onOpen(card.key) } }
                }

                else -> {
                    sameName.firstOrNull()?.let { match ->
                        SectionCaption("Maybe already in your contacts")
                        CardRow(match) { onOpen(match.key) }
                        ActionButton("Add this address there", { add(match) }, Inset.fillMaxWidth(), primary = true)
                    }
                    PlainAction(
                        "New contact",
                        Res.drawable.symbol_person_add,
                        {
                            state.prefill = newDraft(person)
                            onNew()
                        },
                        Modifier.fillMaxWidth(),
                    )
                    if (writable.isNotEmpty()) {
                        PlainAction("Add to another contact…", Res.drawable.symbol_group_add, {
                            picking =
                                true
                        }, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun CardRow(
    card: ContactCard,
    onClick: () -> Unit,
) {
    ContactRow(card.displayName, card.emails.firstOrNull()?.value ?: "No address", card.avatar, onClick)
}

/** A draft named and addressed as the message names [person]. */
private fun newDraft(person: MailSender): ContactDraft {
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

/** Text and controls that are not rows line up with the rows' content. */
private val Inset = Modifier.padding(horizontal = SheetTokens.inset)
