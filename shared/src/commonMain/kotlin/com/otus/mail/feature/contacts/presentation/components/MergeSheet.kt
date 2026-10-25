package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.ClosableSheet
import com.otus.designsystem.components.ContactCardSummary
import com.otus.designsystem.components.PersonChip
import com.otus.designsystem.components.PersonPickRow
import com.otus.designsystem.components.PickMark
import com.otus.designsystem.components.PickRow
import com.otus.designsystem.components.RowDensity
import com.otus.designsystem.components.RowGroup
import com.otus.designsystem.components.SettingToggleRow
import com.otus.designsystem.components.SheetConfirm
import com.otus.designsystem.elements.ActionButton
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_contacts
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.PageTokens
import com.otus.designsystem.theme.PanelTokens
import com.otus.designsystem.theme.SheetTokens
import com.otus.mail.feature.contacts.domain.BookKey
import com.otus.mail.feature.contacts.domain.ContactBook
import com.otus.mail.feature.contacts.domain.MergePlan
import com.otus.mail.feature.contacts.presentation.icon
import com.otus.mail.feature.contacts.presentation.pill

/**
 * Shows two or more cards side by side and what merging them keeps: the name, the addresses, and
 * the book the merged card goes to. Deleting the other cards is its own switch, off at first.
 * "Show as one" joins them in the list without changing the server.
 */
@Composable
internal fun MergeSheet(
    plan: MergePlan,
    books: List<ContactBook>,
    onChange: (MergePlan) -> Unit,
    onShowAsOne: () -> Unit,
    onMerge: (MergePlan) -> Unit,
    onDismiss: () -> Unit,
) {
    val bookOf = { key: BookKey -> books.firstOrNull { it.key == key } }
    val shared =
        plan.cards
            .flatMap { c -> c.emails.map { it.value.lowercase() } }
            .groupBy { it }
            .filterValues { it.size > 1 }
            .keys
    ClosableSheet(
        "Same person?",
        onDismiss,
        SheetConfirm("Merge", { onMerge(plan) }, enabled = plan.addresses.isNotEmpty()),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            SharedIntro(plan, shared)
            plan.cards.forEach { card ->
                val book = card.books.firstNotNullOfOrNull(bookOf)
                ContactCardSummary(
                    if (card.hasName) card.displayName else "No name",
                    book?.name ?: "Contacts",
                    book?.icon ?: Res.drawable.symbol_contacts,
                    card.emails.map { card.pill(it.value) },
                    listOfNotNull(card.phones.firstOrNull()?.value, card.role).joinToString(" · ").ifEmpty { null },
                    Inset,
                )
            }
            // A card without a name would offer its address as one; the address shows as a pill below.
            val named = plan.cards.filter { it.hasName }.distinctBy { it.displayName }
            if (named.isNotEmpty()) {
                ChoiceHeading("Name")
                RowGroup(
                    modifier = Inset,
                    rows =
                        named.map { card ->
                            {
                                PickRow(
                                    card.displayName,
                                    null,
                                    PickMark(plan.name == card.key),
                                    { onChange(plan.copy(name = card.key)) },
                                    density = RowDensity.Compact,
                                )
                            }
                        },
                )
            }
            ChoiceHeading("Email")
            RowGroup(
                modifier = Inset,
                rows =
                    plan.cards
                        .flatMap { card -> card.emails.map { card to it.value } }
                        .distinctBy { it.second.lowercase() }
                        .map { (card, address) ->
                            {
                                val from =
                                    plan.cards.filter { c ->
                                        c.emails.any { it.value.equals(address, ignoreCase = true) }
                                    }
                                val on = address in plan.addresses
                                PersonPickRow(
                                    card.pill(address),
                                    if (from.size > 1) {
                                        "On both cards"
                                    } else {
                                        from
                                            .first()
                                            .books
                                            .firstNotNullOfOrNull(bookOf)
                                            ?.name
                                    },
                                    PickMark(on, multiple = true),
                                    {
                                        val addresses = if (on) plan.addresses - address else plan.addresses + address
                                        onChange(plan.copy(addresses = addresses))
                                    },
                                    density = RowDensity.Compact,
                                )
                            }
                        },
            )
            ChoiceHeading("Save merged card to")
            BookChoices(books, plan.book) { onChange(plan.copy(book = it.key)) }
            val others = plan.cards.filterNot { it == plan.target }
            val name =
                others
                    .singleOrNull()
                    ?.books
                    ?.firstNotNullOfOrNull(bookOf)
                    ?.name
            SettingToggleRow(
                if (name != null) "Delete the $name card" else "Delete the other cards",
                plan.deleteOthers,
                { onChange(plan.copy(deleteOthers = it)) },
                description =
                    if (plan.deleteOthers) "They’re deleted once the merge saves." else "Off: they stay as they are.",
            )
            ActionButton("Show as one", onShowAsOne, Inset.fillMaxWidth())
        }
    }
}

/** Text and controls that are not rows line up with the rows' content. */
private val Inset = Modifier.padding(horizontal = SheetTokens.inset)

/** "These cards share" and the shared address as a pill, then what to do. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SharedIntro(
    plan: MergePlan,
    shared: Set<String>,
) {
    val address = shared.firstOrNull()
    val card = plan.cards.first()
    Column(Inset, verticalArrangement = Arrangement.spacedBy(PageTokens.rowGap)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(PanelTokens.chipGap),
            verticalArrangement = Arrangement.Center,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (address != null) "These cards share" else "These cards may be the same person.",
                color = OtusColors.text2,
            )
            address?.let { PersonChip(card.pill(it)) }
        }
        Text("Pick what the merged card keeps.", color = OtusColors.text2)
    }
}
