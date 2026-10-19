package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import com.otus.designsystem.components.FilterNote
import com.otus.designsystem.components.FilterRowCard
import com.otus.designsystem.components.FilterSection
import com.otus.designsystem.components.PersonChip
import com.otus.designsystem.components.PickerSearch
import com.otus.designsystem.elements.FilterChip
import com.otus.designsystem.theme.FilterTokens
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.RecipientField
import com.otus.mail.feature.inbox.presentation.FilterPeopleSearch
import com.otus.mail.feature.inbox.presentation.FilterPerson
import com.otus.mail.feature.inbox.presentation.hasRecipient
import com.otus.mail.feature.inbox.presentation.hasSender
import com.otus.mail.feature.inbox.presentation.withRecipient
import com.otus.mail.feature.inbox.presentation.withSender

/** The From page: a search, the chosen senders, and the senders of the view or of the search. */
@Composable
internal fun FromPage(
    draft: InboxFilters,
    search: FilterPeopleSearch,
    people: List<FilterPerson>,
    onEdit: FilterEdit,
) {
    FilterPageColumn {
        PickerSearch(search.text, search::search, "Search people")
        val picked = pickedSenders(draft, people)
        if (picked.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FilterTokens.chipGap),
                verticalArrangement = Arrangement.spacedBy(FilterTokens.chipGap),
            ) { picked.forEach { PersonChip(it.recipient, { onEdit(it.remove) }) } }
        }
        val label = if (search.text.isBlank()) "Frequent in this view" else "People found"
        PeopleList(label, people, search.searching) { person ->
            val on = draft.hasSender(person)
            PersonPickRow(person, on, { onEdit { it.withSender(person, !on) } })
        }
    }
}

/**
 * The To page: a search, the fields recipients are looked up in, and one list with the user's
 * own addresses first. A typed address no one in the list has can be chosen as it is.
 */
@Composable
internal fun ToPage(
    draft: InboxFilters,
    search: FilterPeopleSearch,
    people: List<FilterPerson>,
    onEdit: FilterEdit,
) {
    FilterPageColumn {
        PickerSearch(search.text, search::search, "Search people or addresses")
        FilterSection("Received in") {
            RecipientField.entries.forEach { field ->
                val on = field in draft.receivedIn
                FilterChip(field.label, on, { onEdit { it.withField(field, !on) } })
            }
        }
        PeopleList(null, typedAddress(search.text, people) + people, search.searching) { person ->
            val on = draft.hasRecipient(person)
            PersonPickRow(person, on, { onEdit { it.withRecipient(person, !on) } })
        }
    }
}

/** [people] on one card under [label], or a note while there is no one to show. */
@Composable
private fun PeopleList(
    label: String?,
    people: List<FilterPerson>,
    searching: Boolean,
    row: @Composable (FilterPerson) -> Unit,
) {
    when {
        people.isEmpty() -> {
            FilterNote(if (searching) "Searching…" else "No one found")
        }

        label == null -> {
            FilterRowCard(people.map { person -> { row(person) } })
        }

        else -> {
            FilterSection(label) { FilterRowCard(people.map { person -> { row(person) } }) }
        }
    }
}

/** The searched [text] as a person of its own while it is an address no listed person has. */
private fun typedAddress(
    text: String,
    people: List<FilterPerson>,
): List<FilterPerson> {
    val address = text.trim()
    val known = people.any { person -> person.addresses.any { it.equals(address, ignoreCase = true) } }
    return if ('@' in address && ' ' !in address && !known) listOf(FilterPerson("", listOf(address))) else emptyList()
}
