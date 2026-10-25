package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.BirthdayPicker
import com.otus.designsystem.components.ContactFieldRow
import com.otus.designsystem.components.ContactHeader
import com.otus.designsystem.components.ContactValueRow
import com.otus.designsystem.components.InfoText
import com.otus.designsystem.components.InfoTip
import com.otus.designsystem.components.InputSheet
import com.otus.designsystem.components.OptionsItem
import com.otus.designsystem.components.RowGroup
import com.otus.designsystem.components.YearlyDate
import com.otus.designsystem.elements.Chip
import com.otus.designsystem.elements.FieldLabel
import com.otus.designsystem.elements.InputField
import com.otus.designsystem.elements.InputKind
import com.otus.designsystem.elements.SectionCaption
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_add
import com.otus.designsystem.resources.symbol_cake
import com.otus.designsystem.resources.symbol_call
import com.otus.designsystem.resources.symbol_label
import com.otus.designsystem.resources.symbol_notes
import com.otus.designsystem.resources.symbol_person
import com.otus.designsystem.resources.symbol_settings
import com.otus.designsystem.resources.symbol_short_text
import com.otus.designsystem.resources.symbol_work
import com.otus.designsystem.theme.PageTokens
import com.otus.designsystem.theme.PanelTokens
import com.otus.jscontact.PartialDate
import com.otus.mail.feature.contacts.domain.ContactDraft
import com.otus.mail.feature.contacts.domain.ContactLabel
import com.otus.mail.feature.contacts.domain.DraftValue
import com.otus.mail.feature.contacts.presentation.avatar
import com.otus.mail.feature.contacts.presentation.inlineBytes
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

@Composable
internal fun AddChip(
    label: String,
    onClick: () -> Unit,
) {
    Chip(label, onClick) { Symbol(Res.drawable.symbol_add) }
}

internal fun List<DraftValue>.replaced(
    index: Int,
    value: DraftValue,
): List<DraftValue> = mapIndexed { i, old -> if (i == index) value else old }

/** A birthday as the picker takes it; null without a month and day. */
internal fun PartialDate.yearly(): YearlyDate? {
    val month = month ?: return null
    val day = day ?: return null
    return YearlyDate(month.toInt(), day.toInt(), year?.toInt())
}

internal val PrimaryInfo =
    InfoText(
        "Primary address",
        "The starred address is the primary one. Mail fills it in when you pick this person, and the contact page lists it first.",
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
