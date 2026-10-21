package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import com.otus.designsystem.components.AddressInput
import com.otus.designsystem.components.Dropdown
import com.otus.designsystem.components.DropdownRow
import com.otus.designsystem.components.FilterRowCard
import com.otus.designsystem.components.FilterSection
import com.otus.designsystem.components.PersonAvatar
import com.otus.designsystem.components.RecipientLine
import com.otus.designsystem.elements.FilterChip
import com.otus.designsystem.theme.ComposeTokens
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.RecipientField
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.FilterPeopleSearch
import com.otus.mail.feature.inbox.presentation.FilterPerson
import com.otus.mail.feature.inbox.presentation.hasRecipient
import com.otus.mail.feature.inbox.presentation.hasSender
import com.otus.mail.feature.inbox.presentation.typed
import com.otus.mail.feature.inbox.presentation.withRecipient
import com.otus.mail.feature.inbox.presentation.withSender

/** What the People card types into: each line's search and the people their text matches. */
internal class PeopleSearches(
    val senders: FilterPeopleSearch,
    val recipients: FilterPeopleSearch,
    val people: FilterPeople,
)

/**
 * The People card's From and To lines, typed in as a draft's recipient lines are, then "Received
 * in" while someone is picked in To. The draft keeps its fields while the chips are hidden.
 */
@Composable
internal fun PeopleSection(
    draft: InboxFilters,
    options: FilterOptions,
    searches: PeopleSearches,
    onEdit: FilterEdit,
    modifier: Modifier = Modifier,
) {
    val people = searches.people
    FilterSection("People", modifier) {
        FilterRowCard(
            listOf {
                // One row: each line draws its own hairline below, so the card adds no divider.
                Column {
                    FilterPeopleLine(
                        "From",
                        pickedSenders(draft, people.senders),
                        searches.senders,
                        people.senders,
                        draft::hasSender,
                        { person -> onEdit { it.withSender(person, true) } },
                        onEdit,
                    )
                    FilterPeopleLine(
                        "To",
                        pickedRecipients(draft, options, people.recipients),
                        searches.recipients,
                        people.recipients,
                        draft::hasRecipient,
                        { person -> onEdit { it.withRecipient(person, true) } },
                        onEdit,
                    )
                }
            },
        )
    }
    if (draft.sentTo.isNotEmpty() || draft.to.isNotEmpty()) {
        FilterSection("Received in") {
            RecipientField.entries.forEach { field ->
                val on = field in draft.receivedIn
                FilterChip(field.label, on, { onEdit { it.withField(field, !on) } })
            }
        }
    }
}

/**
 * A line of chips for the [picked] people and a field after them. Typing lists the [people] its
 * text matches who are not [isPicked], and tapping one picks them. Enter, or leaving the line,
 * picks a typed address; backspace in the empty field removes the last chip, and tapping a chip
 * takes it back into the field to change, as a draft's recipient line does.
 */
@Composable
private fun FilterPeopleLine(
    label: String,
    picked: List<PickedPerson>,
    search: FilterPeopleSearch,
    people: List<FilterPerson>,
    isPicked: (FilterPerson) -> Boolean,
    onPick: (FilterPerson) -> Unit,
    onEdit: FilterEdit,
) {
    val pick = { person: FilterPerson ->
        onPick(person)
        search.clear()
    }
    Box {
        RecipientLine(
            label,
            picked.map { it.recipient },
            AddressInput(
                search.text,
                onTextChange = search::search,
                onCommit = { people.typed(search.text)?.let(pick) ?: search.dismiss() },
                onRemoveLast = { picked.lastOrNull()?.let { onEdit(it.remove) } },
            ),
            onChipClick = { index ->
                val chip = picked[index]
                onEdit(chip.remove)
                search.search(chip.recipient.name)
            },
        )
        val matches = if (search.suggesting) people.filterNot(isPicked) else emptyList()
        if (matches.isNotEmpty()) {
            Dropdown(search::dismiss, focusable = false) {
                matches.forEach { person ->
                    DropdownRow(
                        person.own?.address ?: person.label,
                        selected = false,
                        onClick = { pick(person) },
                        subtitle = person.subline,
                    ) {
                        val avatar = chipAvatar(person.avatar, person.name, person.addresses.first())
                        PersonAvatar(avatar, ComposeTokens.dropdownAvatarSize)
                    }
                }
            }
        }
    }
}

/**
 * Where the People card sits on its [page], so a press elsewhere on the page can leave the card's
 * lines: the suggestions do not take focus, so only leaving the field closes them.
 */
internal class PeopleCardArea {
    var page: LayoutCoordinates? = null
    var card: LayoutCoordinates? = null

    /** Whether [offset], from the page's corner, lands on the card. */
    fun holds(offset: Offset): Boolean {
        val page = page?.takeIf { it.isAttached }
        val card = card?.takeIf { it.isAttached }
        return page != null && card != null && page.localBoundingBoxOf(card).contains(offset)
    }
}
