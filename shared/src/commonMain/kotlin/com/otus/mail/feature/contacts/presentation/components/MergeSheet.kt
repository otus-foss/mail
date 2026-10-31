package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_person
import com.otus.designsystem.shared.contacts.components.ContactValueRow
import com.otus.designsystem.shared.controls.elements.ActionButton
import com.otus.designsystem.shared.controls.elements.Chip
import com.otus.designsystem.shared.feedback.components.InfoText
import com.otus.designsystem.shared.feedback.components.InfoTip
import com.otus.designsystem.shared.overlays.components.ClosableSheet
import com.otus.designsystem.shared.overlays.components.OptionsItem
import com.otus.designsystem.shared.overlays.components.OptionsMenu
import com.otus.designsystem.shared.overlays.components.SheetConfirm
import com.otus.designsystem.shared.people.components.PersonAvatar
import com.otus.designsystem.shared.rows.components.RowDensity
import com.otus.designsystem.shared.rows.components.RowGroup
import com.otus.designsystem.shared.selection.components.PickMark
import com.otus.designsystem.shared.selection.components.PickRow
import com.otus.designsystem.shared.selection.components.PickRowLayout
import com.otus.designsystem.theme.colors.OtusColors
import com.otus.designsystem.theme.tokens.layout.PanelTokens
import com.otus.designsystem.theme.tokens.overlays.SheetTokens
import com.otus.designsystem.theme.tokens.selection.FilterTokens
import com.otus.mail.feature.contacts.domain.BookKey
import com.otus.mail.feature.contacts.domain.ContactBook
import com.otus.mail.feature.contacts.domain.MergePlan
import com.otus.mail.feature.contacts.presentation.icon
import com.otus.mail.feature.contacts.presentation.owner
import com.otus.mail.feature.contacts.presentation.pill

/**
 * A preview of the card that merging makes, already filled in: tap the name or the book to pick
 * another, tap an address to leave it out, and tick whether the other cards go. Merge saves it;
 * [alternative] is the other way out, such as Show as one, which joins the cards only in Otus,
 * or Show separately for cards already joined that way.
 */
@Composable
internal fun MergeSheet(
    plan: MergePlan,
    books: List<ContactBook>,
    onChange: (MergePlan) -> Unit,
    alternative: String,
    onAlternative: () -> Unit,
    onMerge: (MergePlan) -> Unit,
    onDismiss: () -> Unit,
) {
    val bookOf = { key: BookKey -> books.firstOrNull { it.key == key } }
    ClosableSheet(
        "Same person?",
        onDismiss,
        SheetConfirm("Merge", { onMerge(plan) }, enabled = plan.addresses.isNotEmpty()),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            val sources = plan.cards.mapNotNull { card -> card.books.firstNotNullOfOrNull(bookOf)?.name }.distinct()
            Row(Inset, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "From ${plan.cards.size} cards: ${sources.joinToString(", ")}",
                    Modifier.weight(1f),
                    color = OtusColors.text2,
                )
                InfoTip(MergeInfo)
            }
            RowGroup(
                buildList<@Composable () -> Unit> {
                    add { NameRow(plan, onChange) }
                    add { AddressRow(plan, onChange) }
                    add { BookRow(plan, books, onChange) }
                    if (plan.cards.size > 1) add { DeleteRow(plan, bookOf, onChange) }
                },
                Inset,
            )
            ActionButton(alternative, onAlternative, Inset.fillMaxWidth())
        }
    }
}

/** The merged card's name; with more than one to choose from, a tap offers the others. */
@Composable
private fun NameRow(
    plan: MergePlan,
    onChange: (MergePlan) -> Unit,
) {
    val named = plan.cards.filter { it.hasName }.distinctBy { it.displayName }
    val name =
        plan.cards
            .firstOrNull { it.key == plan.name }
            ?.takeIf { it.hasName }
            ?.displayName ?: "No name"
    var open by remember { mutableStateOf(false) }
    Box {
        ContactValueRow(
            name,
            "Name",
            icon = Res.drawable.symbol_person,
            onClick = { open = true }.takeIf { named.size > 1 },
        )
        if (open) {
            OptionsMenu(
                named.map { card ->
                    OptionsItem.Action(
                        card.displayName,
                        Res.drawable.symbol_person,
                        { onChange(plan.copy(name = card.key)) },
                        current = card.key == plan.name,
                    )
                },
            ) { open = false }
        }
    }
}

/** Every address on the cards as a chip; a tap leaves one out of the merged card or puts it back. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddressRow(
    plan: MergePlan,
    onChange: (MergePlan) -> Unit,
) {
    val addresses =
        plan.cards
            .flatMap { card -> card.emails.map { card to it.value } }
            .distinctBy { it.second.lowercase() }
    FlowRow(
        Modifier.padding(horizontal = SheetTokens.inset - PanelTokens.iconGap),
        horizontalArrangement = Arrangement.spacedBy(PanelTokens.chipGap),
    ) {
        addresses.forEach { (card, address) ->
            val on = address in plan.addresses
            val toggled = if (on) plan.addresses - address else plan.addresses + address
            Chip(address, { onChange(plan.copy(addresses = toggled)) }, selected = on) {
                PersonAvatar(card.pill(address).avatar, FilterTokens.chipAvatarSize)
            }
        }
    }
}

/** The book the merged card is saved in; a tap lists every book it can go to, by account. */
@Composable
private fun BookRow(
    plan: MergePlan,
    books: List<ContactBook>,
    onChange: (MergePlan) -> Unit,
) {
    val book = books.firstOrNull { it.key == plan.book } ?: return
    var open by remember { mutableStateOf(false) }
    Box {
        ContactValueRow(
            book.name,
            "Saved in · ${book.owner()}",
            icon = book.icon,
            onClick = { open = true }.takeIf { books.size > 1 },
        )
        if (open) {
            OptionsMenu(
                books.groupBy { it.owner() }.flatMap { (owner, owned) ->
                    listOf(OptionsItem.Caption(owner)) +
                        owned.map {
                            OptionsItem.Action(
                                it.name,
                                it.icon,
                                { onChange(plan.copy(book = it.key)) },
                                current = it.key == plan.book,
                            )
                        }
                },
            ) { open = false }
        }
    }
}

/** Whether the cards the merge does not save over are deleted; off unless ticked. */
@Composable
private fun DeleteRow(
    plan: MergePlan,
    bookOf: (BookKey) -> ContactBook?,
    onChange: (MergePlan) -> Unit,
) {
    val name =
        plan.cards
            .filterNot { it == plan.target }
            .singleOrNull()
            ?.books
            ?.firstNotNullOfOrNull(bookOf)
            ?.name
    PickRow(
        if (name != null) "Delete the $name card" else "Delete the other cards",
        null,
        PickMark(plan.deleteOthers, multiple = true),
        { onChange(plan.copy(deleteOthers = !plan.deleteOthers)) },
        layout = PickRowLayout(RowDensity.Compact),
    )
}

private val MergeInfo =
    InfoText(
        "Merge or show as one",
        listOf(
            "Merge saves one card with what you picked, in the book you chose. Other contact apps see it too.",
            "Show as one leaves the cards as they are on your server and only lists them as one person " +
                "in Otus.",
        ),
    )

/** Text and controls that are not rows line up with the rows' content. */
private val Inset = Modifier.padding(horizontal = SheetTokens.inset)
