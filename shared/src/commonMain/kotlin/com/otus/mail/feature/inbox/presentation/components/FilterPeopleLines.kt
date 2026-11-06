package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import com.otus.designsystem.mail.compose.components.AddressInput
import com.otus.designsystem.mail.compose.components.RecipientLine
import com.otus.designsystem.shared.controls.elements.Chip
import com.otus.designsystem.shared.controls.elements.ChipLook
import com.otus.designsystem.shared.filters.components.FilterRowCard
import com.otus.designsystem.shared.filters.components.FilterSection
import com.otus.designsystem.shared.people.components.PersonAvatar
import com.otus.designsystem.shared.selection.components.Dropdown
import com.otus.designsystem.shared.selection.components.PickMark
import com.otus.designsystem.shared.selection.components.PickRow
import com.otus.designsystem.shared.selection.components.PickStyle
import com.otus.designsystem.theme.tokens.editing.ComposeTokens
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
    FilterSection("People", modifier) {
        FilterRowCard(
            listOf {
                // One row: each line draws its own hairline below, so the card adds no divider.
                PeopleInputs(draft, options, searches, onEdit)
            },
        )
    }
    if (draft.sentTo.isNotEmpty() || draft.to.isNotEmpty()) {
        FilterSection("Received in") {
            RecipientField.entries.forEach { field ->
                val on = field in draft.receivedIn
                Chip(field.label, { onEdit { it.withField(field, !on) } }, selected = on, look = ChipLook.Raised)
            }
        }
    }
}

@Composable
private fun PeopleInputs(
    draft: InboxFilters,
    options: FilterOptions,
    searches: PeopleSearches,
    onEdit: FilterEdit,
) {
    val people = searches.people
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
                onCommit = {
                    val person = people.typed(search.text)
                    if (person == null) search.dismiss() else pick(person)
                },
                onRemoveLast = {
                    val last = picked.lastOrNull()
                    if (last != null) onEdit(last.remove)
                },
            ),
            onChipClick = { index ->
                val chip = picked[index]
                onEdit(chip.remove)
                // The address, not the name, so leaving the line picks the person again.
                search.search(chip.address)
            },
        )
        val matches = if (search.suggesting) people.filterNot(isPicked) else emptyList()
        if (matches.isNotEmpty()) {
            Dropdown(search::dismiss, focusable = false) {
                matches.forEach { person ->
                    FilterPersonChoice(person) { pick(person) }
                }
            }
        }
    }
}

@Composable
private fun FilterPersonChoice(
    person: FilterPerson,
    onPick: () -> Unit,
) {
    PickRow(person.own?.address ?: person.label, person.subline, PickMark(false, style = PickStyle.Card), onPick) {
        val avatar = chipAvatar(person.avatar, person.name, person.addresses.first())
        PersonAvatar(avatar, ComposeTokens.dropdownAvatarSize)
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
        val pageCoordinates = page?.takeIf { it.isAttached }
        val cardCoordinates = card?.takeIf { it.isAttached }
        return pageCoordinates != null &&
            cardCoordinates != null &&
            pageCoordinates.localBoundingBoxOf(cardCoordinates).contains(offset)
    }
}
