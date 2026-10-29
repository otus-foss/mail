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
import com.otus.designsystem.shared.controls.components.SegmentedToggle
import com.otus.designsystem.shared.overlays.components.ClosableSheet
import com.otus.designsystem.shared.overlays.components.SheetConfirm
import com.otus.designsystem.theme.colors.OtusColors
import com.otus.designsystem.theme.tokens.overlays.SheetTokens
import com.otus.jscontact.Card
import com.otus.mail.feature.contacts.domain.ContactBook
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactFile
import com.otus.mail.feature.contacts.domain.ImportMode
import com.otus.mail.feature.contacts.domain.ImportPlan

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
    ClosableSheet(
        "Import contacts",
        onDismiss,
        SheetConfirm("Import $count", { if (plan != null) onImport(plan, mode) }, enabled = count > 0),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            val cards = if (file.cards.size == 1) "1 card" else "${file.cards.size} cards"
            Text("${file.name} · $cards", Inset, color = OtusColors.text2)
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
    SegmentedToggle(ImportMode.entries.map { it.label }, mode.ordinal, { onMode(ImportMode.entries[it]) }, Inset)
    Text(mode.note, Inset, color = OtusColors.text2)
}

/** The cards the file could not read, by position when known, with why. */
@Composable
private fun Failures(failures: List<Pair<Int, String>>) {
    if (failures.isEmpty()) return
    val cards = if (failures.size == 1) "1 card can’t be read" else "${failures.size} cards can’t be read"
    ChoiceHeading(cards)
    failures.forEach { (position, reason) ->
        Text(if (position > 0) "Card $position: $reason" else reason, Inset, color = OtusColors.text2)
    }
}

/** The first few cards by name and address, tagged with [mode] where the person is already in the book. */
@Composable
private fun Preview(
    cards: List<Card>,
    plan: ImportPlan?,
    mode: ImportMode,
) {
    if (cards.isEmpty()) return
    ChoiceHeading("Preview")
    cards.take(PREVIEW).forEach { card ->
        val firstEmail = card.emails?.values?.firstOrNull()
        val address = firstEmail?.address
        val name = card.name?.full ?: address ?: "No name"
        val tag = mode.label.takeIf { plan?.match(card) != null }
        Text(listOfNotNull(name, address, tag).joinToString(" · "), Inset, maxLines = 1)
    }
    if (cards.size > PREVIEW) Text("and ${cards.size - PREVIEW} more", Inset, color = OtusColors.text2)
}

/** How many cards the preview lists before it sums up the rest. */
private const val PREVIEW = 5

/** Text and controls that are not rows line up with the rows' content. */
private val Inset = Modifier.padding(horizontal = SheetTokens.inset)
