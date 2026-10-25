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
import com.otus.designsystem.resources.symbol_badge
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
            FieldGroup("Email", action = if (draft.emails.size > 1) ({ InfoTip(PrimaryInfo) }) else null) {
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
                            onChange(
                                draft.copy(
                                    emails =
                                        draft.emails.mapIndexed { i, it ->
                                            it.copy(
                                                preferred =
                                                    i == index,
                                            )
                                        },
                                ),
                            )
                        },
                        onRemove = { onChange(draft.copy(emails = draft.emails.filterIndexed { i, _ -> i != index })) },
                    )
                }
            }
        }
        if (draft.phones.isNotEmpty()) {
            FieldGroup("Phone") {
                draft.phones.forEachIndexed { index, phone ->
                    ContactFieldRow(
                        phone.value,
                        {
                            val number = it.filter(::isPhoneChar)
                            onChange(draft.copy(phones = draft.phones.replaced(index, phone.copy(value = number))))
                        },
                        "Phone ${index + 1}",
                        phone.label.text,
                        labelChoices(phone.label, labels, { managing = relabelPhone(index) }, relabelPhone(index)),
                        placeholder = "+1 202 555 0143",
                        kind = InputKind.Phone,
                        icon = Res.drawable.symbol_call,
                        onRemove = { onChange(draft.copy(phones = draft.phones.filterIndexed { i, _ -> i != index })) },
                    )
                }
            }
        }
        // Side by side like the name, once both are added; either alone takes the full width.
        if (draft.organization != null || draft.title != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(PageTokens.rowGap)) {
                draft.organization?.let {
                    InputField(
                        "Organization",
                        it,
                        { value -> onChange(draft.copy(organization = value)) },
                        Modifier.weight(1f),
                        icon = Res.drawable.symbol_work,
                        onRemove = { onChange(draft.copy(organization = null)) },
                    )
                }
                draft.title?.let {
                    InputField(
                        "Title",
                        it,
                        { value -> onChange(draft.copy(title = value)) },
                        Modifier.weight(1f),
                        icon = Res.drawable.symbol_badge,
                        onRemove = { onChange(draft.copy(title = null)) },
                    )
                }
            }
        }
        var pickingBirthday by remember { mutableStateOf(false) }
        draft.birthday?.let { birthday ->
            FieldGroup("Birthday") {
                RowGroup(
                    listOf {
                        ContactValueRow(
                            birthday.text(),
                            null,
                            icon = Res.drawable.symbol_cake,
                            onClick = { pickingBirthday = true },
                            onRemove = { onChange(draft.copy(birthday = null)) },
                        )
                    },
                    raised = true,
                )
            }
        }
        if (pickingBirthday) {
            BirthdayPicker(
                draft.birthday?.yearly(),
                onDismiss = { pickingBirthday = false },
                onConfirm = {
                    val date = PartialDate(year = it.year?.toLong(), month = it.month.toLong(), day = it.day.toLong())
                    onChange(draft.copy(birthday = date))
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
        draft.note?.let {
            InputField(
                "Note",
                it,
                { value -> onChange(draft.copy(note = value)) },
                icon = Res.drawable.symbol_notes,
                onRemove = { onChange(draft.copy(note = null)) },
            )
        }
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
