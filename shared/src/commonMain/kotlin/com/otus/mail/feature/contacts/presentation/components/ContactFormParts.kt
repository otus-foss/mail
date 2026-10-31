package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_add
import com.otus.designsystem.resources.symbol_label
import com.otus.designsystem.resources.symbol_person
import com.otus.designsystem.resources.symbol_settings
import com.otus.designsystem.resources.symbol_short_text
import com.otus.designsystem.resources.symbol_work
import com.otus.designsystem.shared.controls.elements.Chip
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.dates.components.YearlyDate
import com.otus.designsystem.shared.feedback.components.InfoText
import com.otus.designsystem.shared.input.elements.FieldLabel
import com.otus.designsystem.shared.overlays.components.OptionsItem
import com.otus.designsystem.theme.tokens.layout.PageTokens
import com.otus.designsystem.theme.tokens.layout.PanelTokens
import com.otus.jscontact.PartialDate
import com.otus.mail.feature.contacts.domain.ContactLabel
import com.otus.mail.feature.contacts.domain.DraftValue
import com.otus.mail.feature.contacts.presentation.text
import org.jetbrains.compose.resources.DrawableResource

/**
 * The labels JSContact gives an address or number (RFC 9553, section 1.5.1): the `work` and
 * `private` contexts, none, or a free-text label. Custom labels already in use, [known], are
 * offered by name; "Labels…" opens the sheet that adds and deletes them.
 */
internal fun labelChoices(
    current: ContactLabel,
    known: List<String>,
    onManage: () -> Unit,
    onPick: (ContactLabel) -> Unit,
): List<OptionsItem> {
    fun choice(
        label: ContactLabel,
        icon: DrawableResource,
    ) = OptionsItem.Action(label.text, icon, { onPick(label) }, current = label == current)
    return listOf(
        choice(ContactLabel.Work, Res.drawable.symbol_work),
        choice(ContactLabel.Personal, Res.drawable.symbol_person),
        choice(ContactLabel.Other, Res.drawable.symbol_short_text),
    ) + known.map { choice(ContactLabel.Custom(it), Res.drawable.symbol_label) } +
        OptionsItem.Action("Manage labels…", Res.drawable.symbol_settings, onManage, opens = true)
}

/** A chip with a + that adds the field it names. */
@Composable
internal fun AddChip(
    label: String,
    onClick: () -> Unit,
) {
    Chip(label, onClick) { Symbol(Res.drawable.symbol_add) }
}

/** The list with the value at [index] swapped for [value]. */
internal fun List<DraftValue>.replaced(
    index: Int,
    value: DraftValue,
): List<DraftValue> = mapIndexed { i, old -> if (i == index) value else old }

/** A birthday as the picker takes it; null without a month and day. */
internal fun PartialDate.yearly(): YearlyDate? {
    val monthNumber = month ?: return null
    return day?.let { YearlyDate(monthNumber.toInt(), it.toInt(), year?.toInt()) }
}

/** The tip that explains the star by the primary address. */
internal val PrimaryInfo =
    InfoText(
        "Primary address",
        "Mail fills in the starred address when you pick this person.",
    )

/** Digits and the marks people write numbers with, such as "+41 (0)79 123-45-67" or "*31#". */
internal fun isPhoneChar(char: Char): Boolean = char.isDigit() || char in "+-() ./#*"

/** A [FieldLabel] over fields that belong together, such as every address, as close as any field's label. */
@Composable
internal fun FieldGroup(
    label: String,
    action: (@Composable () -> Unit)? = null,
    fields: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PanelTokens.iconGap)) {
        FieldLabel(label, action = action)
        Column(verticalArrangement = Arrangement.spacedBy(PageTokens.rowGap)) { fields() }
    }
}
