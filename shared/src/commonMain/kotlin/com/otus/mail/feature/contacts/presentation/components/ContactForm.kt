package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.ContactFieldLook
import com.otus.designsystem.components.ContactFieldRow
import com.otus.designsystem.components.ContactHeader
import com.otus.designsystem.components.InfoTip
import com.otus.designsystem.components.OptionsItem
import com.otus.designsystem.components.PrimaryStar
import com.otus.designsystem.elements.InputField
import com.otus.designsystem.elements.InputKind
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_call
import com.otus.designsystem.theme.PageTokens
import com.otus.mail.feature.contacts.domain.ContactDraft
import com.otus.mail.feature.contacts.domain.ContactLabel
import com.otus.mail.feature.contacts.domain.DraftValue
import com.otus.mail.feature.contacts.presentation.avatar
import com.otus.mail.feature.contacts.presentation.inlineBytes
import com.otus.mail.feature.contacts.presentation.text

/**
 * The editor's fields: the photo, which opens [photoChoices], name, addresses with a label and a
 * preferred star, numbers, and the optional fields, which stay hidden until added from one row of
 * + chips. The birthday opens date wheels.
 */
@Composable
internal fun ContactForm(
    draft: ContactDraft,
    onChange: (ContactDraft) -> Unit,
    knownLabels: ContactLabels,
    modifier: Modifier = Modifier,
    photoChoices: List<OptionsItem> = emptyList(),
) {
    // Set while the labels sheet is open; it labels the field whose menu opened it.
    var managing by remember { mutableStateOf<((ContactLabel) -> Unit)?>(null) }
    val labels = draft.labelNames(knownLabels)
    ContactLabelSheets(draft, onChange, knownLabels, labels, managing) { managing = null }
    val menu = LabelMenu(labels) { managing = it }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PageTokens.rowGap)) {
        val photo = remember(draft.photo) { draft.photo?.inlineBytes() }
        ContactHeader(draft.avatar(photo), emptyList(), photoChoices = photoChoices)
        NameRow(draft, onChange)
        EmailGroup(draft, onChange, menu)
        PhoneGroup(draft, onChange, menu)
        OrganizationRow(draft, onChange)
        var pickingBirthday by remember { mutableStateOf(false) }
        BirthdayField(draft, onChange, pickingBirthday) { pickingBirthday = it }
        NoteField(draft, onChange)
        AddFieldChips(draft, onChange) { pickingBirthday = true }
    }
}

/** What a field's label menu offers: the custom label [names], and [onManage], which opens the labels sheet. */
private class LabelMenu(
    val names: List<String>,
    val onManage: (relabel: (ContactLabel) -> Unit) -> Unit,
) {
    /** The menu for a field labelled [current]; [onPick] gives the field a label, here or from the sheet. */
    fun choices(
        current: ContactLabel,
        onPick: (ContactLabel) -> Unit,
    ): List<OptionsItem> = labelChoices(current, names, { onManage(onPick) }, onPick)
}

/** Known custom labels plus the ones typed in this form, so a second field can reuse one before saving. */
private fun ContactDraft.labelNames(known: ContactLabels): List<String> =
    (known.names + (emails + phones).mapNotNull { it.label.custom?.trim() })
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }

/** First and last name side by side. */
@Composable
private fun NameRow(
    draft: ContactDraft,
    onChange: (ContactDraft) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(PageTokens.rowGap)) {
        InputField("First name", draft.given, { onChange(draft.copy(given = it)) }, Modifier.weight(1f))
        InputField("Last name", draft.surname, { onChange(draft.copy(surname = it)) }, Modifier.weight(1f))
    }
}

/**
 * Every address, each checked as typed, with a label menu, ✕, and a star for the primary one. With
 * several addresses, a tip explains the star; with none starred, the first one is the primary.
 */
@Composable
private fun EmailGroup(
    draft: ContactDraft,
    onChange: (ContactDraft) -> Unit,
    menu: LabelMenu,
) {
    if (draft.emails.isEmpty()) return
    val firstIsPrimary = draft.emails.size > 1 && draft.emails.none { it.preferred }
    FieldGroup("Email", action = if (draft.emails.size > 1) ({ InfoTip(PrimaryInfo) }) else null) {
        draft.emails.forEachIndexed { index, email ->
            val update = { value: DraftValue -> onChange(draft.copy(emails = draft.emails.replaced(index, value))) }
            ContactFieldRow(
                email.value,
                { update(email.copy(value = it)) },
                "Email ${index + 1}",
                ContactFieldLook(
                    email.label.text,
                    menu.choices(email.label) { update(email.copy(label = it)) },
                    placeholder = "name@example.com",
                    kind = InputKind.Email,
                    error = "Not a valid email address".takeIf { email.isInvalidEmail },
                ),
                primary =
                    PrimaryStar(email.preferred || (index == 0 && firstIsPrimary)) {
                        val starred = draft.emails.mapIndexed { i, value -> value.copy(preferred = i == index) }
                        onChange(draft.copy(emails = starred))
                    },
                onRemove = { onChange(draft.copy(emails = draft.emails.filterIndexed { i, _ -> i != index })) },
            )
        }
    }
}

/** Every number, keeping only the characters people write numbers with, each with a label menu and ✕. */
@Composable
private fun PhoneGroup(
    draft: ContactDraft,
    onChange: (ContactDraft) -> Unit,
    menu: LabelMenu,
) {
    if (draft.phones.isEmpty()) return
    FieldGroup("Phone") {
        draft.phones.forEachIndexed { index, phone ->
            val update = { value: DraftValue -> onChange(draft.copy(phones = draft.phones.replaced(index, value))) }
            ContactFieldRow(
                phone.value,
                { update(phone.copy(value = it.filter(::isPhoneChar))) },
                "Phone ${index + 1}",
                ContactFieldLook(
                    phone.label.text,
                    menu.choices(phone.label) { update(phone.copy(label = it)) },
                    placeholder = "+1 202 555 0143",
                    kind = InputKind.Phone,
                    icon = Res.drawable.symbol_call,
                ),
                onRemove = { onChange(draft.copy(phones = draft.phones.filterIndexed { i, _ -> i != index })) },
            )
        }
    }
}
