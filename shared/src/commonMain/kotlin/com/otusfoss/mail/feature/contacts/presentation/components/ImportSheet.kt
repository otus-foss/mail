package com.otusfoss.mail.feature.contacts.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_expand_less
import com.otusfoss.core.designsystem.resources.symbol_expand_more
import com.otusfoss.core.designsystem.shared.contacts.components.ContactValueRow
import com.otusfoss.core.designsystem.shared.controls.components.SegmentedToggle
import com.otusfoss.core.designsystem.shared.controls.elements.IconButton
import com.otusfoss.core.designsystem.shared.controls.elements.IconButtonLook
import com.otusfoss.core.designsystem.shared.controls.elements.Symbol
import com.otusfoss.core.designsystem.shared.controls.elements.UiAction
import com.otusfoss.core.designsystem.shared.overlays.components.SheetFrame
import com.otusfoss.core.designsystem.shared.overlays.elements.sheetInset
import com.otusfoss.core.designsystem.shared.rows.components.Collapsible
import com.otusfoss.core.designsystem.shared.rows.components.RowGroup
import com.otusfoss.core.designsystem.theme.colors.OtusColors
import com.otusfoss.core.designsystem.theme.tokens.core.Spacing
import com.otusfoss.core.designsystem.theme.tokens.overlays.SheetTokens
import com.otusfoss.core.jscontact.Card
import com.otusfoss.mail.feature.contacts.domain.ContactBook
import com.otusfoss.mail.feature.contacts.domain.ContactCard
import com.otusfoss.mail.feature.contacts.domain.ContactFile
import com.otusfoss.mail.feature.contacts.domain.ImportMode
import com.otusfoss.mail.feature.contacts.domain.ImportPlan

/**
 * Confirms a vCard import: which book the cards go to, what happens to people already in it,
 * the cards the file holds, and the ones it could not read with why.
 */
@Composable
internal fun ImportSheet(
    file: ContactFile,
    books: List<ContactBook>,
    existing: List<ContactCard>,
    onImport: (ImportPlan, ImportMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var book by remember { mutableStateOf(books.firstOrNull { it.isDefault } ?: books.firstOrNull()) }
    var mode by remember { mutableStateOf(ImportMode.Merge) }
    val plan = book?.let { ImportPlan(file.cards, it.key, existing, file.failures) }
    val count = plan?.count(mode) ?: 0
    SheetFrame(
        "Import contacts",
        onDismiss,
        done = UiAction("Import $count", enabled = count > 0, onClick = { if (plan != null) onImport(plan, mode) }),
        closeButton = true,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            val cards = if (file.cards.size == 1) "1 card" else "${file.cards.size} cards"
            Text("${file.name} · $cards", Modifier.sheetInset(), color = OtusColors.text2)
            ChoiceHeading("Import into")
            BookChoices(books, book?.key) { book = it }
            val chosen = book
            if (chosen != null && plan != null) MatchedPeople(plan, chosen, mode) { mode = it }
            Failures(file.failures)
            Preview(file.cards, plan, mode)
        }
    }
}

/** For cards whose people are already in [book]: the toggle between the ways to import them. */
@Composable
private fun MatchedPeople(
    plan: ImportPlan,
    book: ContactBook,
    mode: ImportMode,
    onMode: (ImportMode) -> Unit,
) {
    val matched = plan.matched.size
    if (matched == 0) return
    val people = if (matched == 1) "1 person is" else "$matched people are"
    ChoiceHeading("$people already in ${book.name}")
    SegmentedToggle(
        ImportMode.entries.map {
            it.label
        },
        mode.ordinal,
        { onMode(ImportMode.entries[it]) },
        Modifier.sheetInset(),
    )
    Text(mode.note, Modifier.sheetInset(), color = OtusColors.text2)
}

/** The cards the file could not read, by position when known, with why. */
@Composable
private fun Failures(failures: List<Pair<Int, String>>) {
    if (failures.isEmpty()) return
    val cards = if (failures.size == 1) "1 card can’t be read" else "${failures.size} cards can’t be read"
    ChoiceHeading(cards)
    failures.forEach { (position, reason) ->
        Text(if (position > 0) "Card $position: $reason" else reason, Modifier.sheetInset(), color = OtusColors.text2)
    }
}

/**
 * Every card by name over address, folded away until the heading is tapped. A card whose person is
 * already in the book carries [mode] as its label.
 */
@Composable
private fun Preview(
    cards: List<Card>,
    plan: ImportPlan?,
    mode: ImportMode,
) {
    if (cards.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    PreviewHeading(expanded) { expanded = !expanded }
    Collapsible(expanded) {
        RowGroup(
            cards.map { card ->
                { PreviewRow(card, mode.label.takeIf { plan?.match(card) != null }) }
            },
            Modifier.sheetInset(),
        )
    }
}

@Composable
private fun PreviewHeading(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val action = if (expanded) "Hide preview" else "Show preview"
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = action, onClick = onToggle)
            .padding(start = SheetTokens.inset, end = Spacing.iconGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Preview", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        IconButton(action, onToggle, look = IconButtonLook.Bare) {
            Symbol(if (expanded) Res.drawable.symbol_expand_less else Res.drawable.symbol_expand_more)
        }
    }
}

/** A card's name over its first address, or the address alone when the card has no other name. */
@Composable
private fun PreviewRow(
    card: Card,
    label: String?,
) {
    val address =
        card.emails
            ?.values
            ?.firstOrNull()
            ?.address
    val name = card.name?.full ?: address ?: "No name"
    ContactValueRow(name, address.takeIf { it != name }, label = label)
}
