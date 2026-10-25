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
import com.otus.designsystem.components.SegmentedToggle
import com.otus.designsystem.components.SheetConfirm
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.SheetTokens
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
        SheetConfirm("Import $count", { plan?.let { onImport(it, mode) } }, enabled = count > 0),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            val cards = if (file.cards.size == 1) "1 card" else "${file.cards.size} cards"
            Text("${file.name} · $cards", Inset, color = OtusColors.text2)
            ChoiceHeading("Import into")
            BookChoices(books, book?.key) { book = it }
            if (plan != null && plan.matched.isNotEmpty()) {
                val people = if (plan.matched.size == 1) "1 person is" else "${plan.matched.size} people are"
                ChoiceHeading("$people already in ${book?.name}")
                SegmentedToggle(
                    ImportMode.entries.map { it.label },
                    mode.ordinal,
                    { mode = ImportMode.entries[it] },
                    Inset,
                )
                Text(mode.note, Inset, color = OtusColors.text2)
            }
            if (file.failures.isNotEmpty()) {
                val failed =
                    if (file.failures.size ==
                        1
                    ) {
                        "1 card can’t be read"
                    } else {
                        "${file.failures.size} cards can’t be read"
                    }
                ChoiceHeading(failed)
                file.failures.forEach { (position, reason) ->
                    Text(if (position > 0) "Card $position: $reason" else reason, Inset, color = OtusColors.text2)
                }
            }
            if (file.cards.isNotEmpty()) {
                ChoiceHeading("Preview")
                file.cards.take(PREVIEW).forEach { card ->
                    val name =
                        card.name?.full ?: card.emails
                            ?.values
                            ?.firstOrNull()
                            ?.address ?: "No name"
                    val address =
                        card.emails
                            ?.values
                            ?.firstOrNull()
                            ?.address
                    val tag = mode.label.takeIf { plan?.match(card) != null }
                    Text(listOfNotNull(name, address, tag).joinToString(" · "), Inset, maxLines = 1)
                }
                if (file.cards.size >
                    PREVIEW
                ) {
                    Text("and ${file.cards.size - PREVIEW} more", Inset, color = OtusColors.text2)
                }
            }
        }
    }
}

private const val PREVIEW = 5

/** Text and controls that are not rows line up with the rows' content. */
private val Inset = Modifier.padding(horizontal = SheetTokens.inset)
