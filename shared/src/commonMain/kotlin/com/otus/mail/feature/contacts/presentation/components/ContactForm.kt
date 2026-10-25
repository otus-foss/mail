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
import com.otus.designsystem.elements.InputField
import com.otus.designsystem.elements.InputKind
import com.otus.designsystem.elements.SectionCaption
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_add
import com.otus.designsystem.resources.symbol_cake
import com.otus.designsystem.resources.symbol_label
import com.otus.designsystem.resources.symbol_person
import com.otus.designsystem.resources.symbol_settings
import com.otus.designsystem.resources.symbol_short_text
import com.otus.designsystem.resources.symbol_work
import com.otus.designsystem.theme.PageTokens
import com.otus.jscontact.PartialDate
import com.otus.mail.feature.contacts.domain.ContactDraft
import com.otus.mail.feature.contacts.domain.ContactLabel
import com.otus.mail.feature.contacts.domain.DraftValue
import com.otus.mail.feature.contacts.presentation.avatar
import com.otus.mail.feature.contacts.presentation.inlineBytes
import com.otus.mail.feature.contacts.presentation.text
import org.jetbrains.compose.resources.DrawableResource

/**
 * The editor's fields: the photo, which opens [photoChoices], name, addresses with a label and a preferred star, numbers, and the
 * optional fields, which stay hidden until added from one row of + chips. The birthday opens date wheels.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ContactForm(
    draft: ContactDraft,
    onChange: (ContactDraft) -> Unit,
    knownLabels: ContactLabels,
    modifier: Modifier = Modifier,
    photoChoices: List<OptionsItem> = emptyList(),
) {
    // Set while the custom label sheet is open; it applies the typed label to its field.
    var naming by remember { mutableStateOf<((ContactLabel) -> Unit)?>(null) }
    naming?.let { apply ->
        InputSheet(
            title = "Custom label",
            label = "Label",
            confirmLabel = "Use",
            onConfirm = { text ->
                apply(ContactLabel.Custom(text.trim()))
                naming = null
            },
            onDismiss = { naming = null },
            problem = { if (it.isBlank()) "" else null },
        )
    }
    // Labels typed in this form count too, so a second field can reuse one before saving.
    val labels =
        (knownLabels.names + (draft.emails + draft.phones).mapNotNull { it.label.custom?.trim() })
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
    // Set while the labels sheet is open; it labels the field whose menu opened it.
    var managing by remember { mutableStateOf<((ContactLabel) -> Unit)?>(null) }
    managing?.let { apply ->
        // Deleting also clears the label from this form's fields, which are not saved yet.
        val clear = { label: String ->
            val cleared = { values: List<DraftValue> ->
                values.map {
                    if (it.label.custom.equals(
                            label,
                            ignoreCase = true,
                        )
                    ) {
                        it.copy(label = ContactLabel.Other)
                    } else {
                        it
                    }
                }
            }
            onChange(draft.copy(emails = cleared(draft.emails), phones = cleared(draft.phones)))
            knownLabels.delete(label)
        }
        LabelsSheet(
            // Labels typed in this form show too, before the contact is saved.
            ContactLabels(labels, knownLabels.uses, clear),
            onNew = {
                managing = null
                naming = apply
            },
        ) { managing = null }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PageTokens.rowGap)) {
        val photo = remember(draft.photo) { draft.photo?.inlineBytes() }
        val relabelEmail = { index: Int ->
            { label: ContactLabel ->
                onChange(draft.copy(emails = draft.emails.replaced(index, draft.emails[index].copy(label = label))))
            }
        }
        val relabelPhone = { index: Int ->
            { label: ContactLabel ->
                onChange(draft.copy(phones = draft.phones.replaced(index, draft.phones[index].copy(label = label))))
            }
        }
        // With several addresses and none starred, the first one is the primary.
        val firstIsPrimary = draft.emails.size > 1 && draft.emails.none { it.preferred }
        ContactHeader(draft.avatar(photo), emptyList(), photoChoices = photoChoices)
        Row(horizontalArrangement = Arrangement.spacedBy(PageTokens.rowGap)) {
            InputField("First name", draft.given, { onChange(draft.copy(given = it)) }, Modifier.weight(1f))
            InputField("Last name", draft.surname, { onChange(draft.copy(surname = it)) }, Modifier.weight(1f))
        }
        if (draft.emails.isNotEmpty()) {
            SectionCaption(
                "Email",
                action =
                    if (draft.emails.size >
                        1
                    ) {
                        ({ InfoTip(PrimaryInfo) })
                    } else {
                        null
                    },
            )
        }
        draft.emails.forEachIndexed { index, email ->
            ContactFieldRow(
                email.value,
                { onChange(draft.copy(emails = draft.emails.replaced(index, email.copy(value = it)))) },
                "Email ${index + 1}",
                email.label.text,
                labelChoices(email.label, labels, { managing = relabelEmail(index) }, relabelEmail(index)),
                placeholder = "name@example.com",
                kind = InputKind.Email,
                error = "Not a valid email address".takeIf { email.isInvalidEmail },
                preferred = email.preferred || (index == 0 && firstIsPrimary),
                onPreferredClick = {
                    onChange(draft.copy(emails = draft.emails.mapIndexed { i, it -> it.copy(preferred = i == index) }))
                },
            )
        }
        if (draft.phones.isNotEmpty()) SectionCaption("Phone")
        draft.phones.forEachIndexed { index, phone ->
            ContactFieldRow(
                phone.value,
                { onChange(draft.copy(phones = draft.phones.replaced(index, phone.copy(value = it)))) },
                "Phone ${index + 1}",
                phone.label.text,
                labelChoices(phone.label, labels, { managing = relabelPhone(index) }, relabelPhone(index)),
                placeholder = "+1 202 555 0143",
                kind = InputKind.Text,
            )
        }
        draft.organization?.let {
            InputField(
                "Organization",
                it,
                { value -> onChange(draft.copy(organization = value)) },
            )
        }
        draft.title?.let { InputField("Title", it, { value -> onChange(draft.copy(title = value)) }) }
        var pickingBirthday by remember { mutableStateOf(false) }
        draft.birthday?.let { birthday ->
            RowGroup(
                listOf {
                    ContactValueRow(birthday.text(), "Birthday", icon = Res.drawable.symbol_cake, onClick = {
                        pickingBirthday =
                            true
                    })
                },
                raised = true,
            )
        }
        if (pickingBirthday) {
            BirthdayPicker(
                draft.birthday?.yearly(),
                onDismiss = { pickingBirthday = false },
                onConfirm = {
                    onChange(
                        draft.copy(
                            birthday =
                                PartialDate(
                                    year = it.year?.toLong(),
                                    month = it.month.toLong(),
                                    day = it.day.toLong(),
                                ),
                        ),
                    )
                    pickingBirthday = false
                },
                onRemove =
                    draft.birthday?.let {
                        {
                            onChange(draft.copy(birthday = null))
                            pickingBirthday = false
                        }
                    },
            )
        }
        draft.note?.let { InputField("Note", it, { value -> onChange(draft.copy(note = value)) }) }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(PageTokens.rowGap),
            verticalArrangement = Arrangement.spacedBy(PageTokens.rowGap),
        ) {
            // Email and Phone stay, since a contact can have several of each.
            AddChip("Email") { onChange(draft.copy(emails = draft.emails + DraftValue())) }
            AddChip("Phone") { onChange(draft.copy(phones = draft.phones + DraftValue())) }
            if (draft.organization == null) AddChip("Organization") { onChange(draft.copy(organization = "")) }
            if (draft.title == null) AddChip("Title") { onChange(draft.copy(title = "")) }
            if (draft.birthday == null) AddChip("Birthday") { pickingBirthday = true }
            if (draft.note == null) AddChip("Note") { onChange(draft.copy(note = "")) }
        }
    }
}

/**
 * The labels JSContact gives an address or number (RFC 9553, section 1.5.1): the `work` and
 * `private` contexts, none, or a free-text label. Custom labels already in use, [known], are
 * offered by name; "Labels…" opens the sheet that adds and deletes them.
 */
private fun labelChoices(
    current: ContactLabel,
    known: List<String>,
    onManage: () -> Unit,
    onPick: (ContactLabel) -> Unit,
): List<OptionsItem> {
    fun choice(
        label: ContactLabel,
        icon: DrawableResource,
    ) = OptionsItem.Action(label.text, icon, { onPick(label) }, detail = "Current".takeIf { label == current })
    return listOf(
        choice(ContactLabel.Work, Res.drawable.symbol_work),
        choice(ContactLabel.Personal, Res.drawable.symbol_person),
        choice(ContactLabel.Other, Res.drawable.symbol_short_text),
    ) + known.map { choice(ContactLabel.Custom(it), Res.drawable.symbol_label) } +
        OptionsItem.Action("Manage labels…", Res.drawable.symbol_settings, onManage, opens = true)
}

@Composable
private fun AddChip(
    label: String,
    onClick: () -> Unit,
) {
    Chip(label, onClick) { Symbol(Res.drawable.symbol_add) }
}

private fun List<DraftValue>.replaced(
    index: Int,
    value: DraftValue,
): List<DraftValue> = mapIndexed { i, old -> if (i == index) value else old }

/** A birthday as the picker takes it; null without a month and day. */
private fun PartialDate.yearly(): YearlyDate? {
    val month = month ?: return null
    val day = day ?: return null
    return YearlyDate(month.toInt(), day.toInt(), year?.toInt())
}

private val PrimaryInfo =
    InfoText(
        "Primary address",
        "The starred address is the primary one. Mail fills it in when you pick this person, and the contact page lists it first.",
    )
