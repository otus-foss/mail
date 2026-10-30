package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_badge
import com.otus.designsystem.resources.symbol_cake
import com.otus.designsystem.resources.symbol_notes
import com.otus.designsystem.resources.symbol_work
import com.otus.designsystem.shared.contacts.components.ContactInputField
import com.otus.designsystem.shared.contacts.components.ContactPickerField
import com.otus.designsystem.shared.dates.components.BirthdayPicker
import com.otus.designsystem.theme.tokens.layout.PageTokens
import com.otus.jscontact.PartialDate
import com.otus.mail.feature.contacts.domain.ContactDraft
import com.otus.mail.feature.contacts.domain.DraftValue
import com.otus.mail.feature.contacts.presentation.text

/** Organization and title side by side like the name, once both are added; either alone takes the full width. */
@Composable
internal fun OrganizationRow(
    draft: ContactDraft,
    onChange: (ContactDraft) -> Unit,
) {
    if (draft.organization == null && draft.title == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(PageTokens.rowGap)) {
        draft.organization?.let {
            ContactInputField(
                "Organization",
                it,
                { value -> onChange(draft.copy(organization = value)) },
                Res.drawable.symbol_work,
                { onChange(draft.copy(organization = null)) },
                Modifier.weight(1f),
            )
        }
        draft.title?.let {
            ContactInputField(
                "Title",
                it,
                { value -> onChange(draft.copy(title = value)) },
                Res.drawable.symbol_badge,
                { onChange(draft.copy(title = null)) },
                Modifier.weight(1f),
            )
        }
    }
}

/**
 * The birthday, once added, and its date wheels while [picking]. The wheels open from the field or
 * from the Birthday chip, so the caller owns [picking]; [onPicking] closes them again.
 */
@Composable
internal fun BirthdayField(
    draft: ContactDraft,
    onChange: (ContactDraft) -> Unit,
    picking: Boolean,
    onPicking: (Boolean) -> Unit,
) {
    draft.birthday?.let { birthday ->
        ContactPickerField(
            "Birthday",
            birthday.text(),
            Res.drawable.symbol_cake,
            onClick = { onPicking(true) },
            onRemove = { onChange(draft.copy(birthday = null)) },
        )
    }
    if (!picking) return
    BirthdayPicker(
        draft.birthday?.yearly(),
        onDismiss = { onPicking(false) },
        onConfirm = {
            val date = PartialDate(year = it.year?.toLong(), month = it.month.toLong(), day = it.day.toLong())
            onChange(draft.copy(birthday = date))
            onPicking(false)
        },
        onRemove =
            draft.birthday?.let {
                {
                    onChange(draft.copy(birthday = null))
                    onPicking(false)
                }
            },
    )
}

/** The note, once added, with ✕ to drop it again. */
@Composable
internal fun NoteField(
    draft: ContactDraft,
    onChange: (ContactDraft) -> Unit,
) {
    val note = draft.note ?: return
    ContactInputField(
        "Note",
        note,
        { value -> onChange(draft.copy(note = value)) },
        Res.drawable.symbol_notes,
        { onChange(draft.copy(note = null)) },
    )
}

/**
 * The + chips that add fields. Email and Phone stay, since a contact can have several of each; the
 * others leave once added. Birthday opens the date wheels through [onAddBirthday].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddFieldChips(
    draft: ContactDraft,
    onChange: (ContactDraft) -> Unit,
    onAddBirthday: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(PageTokens.rowGap),
        verticalArrangement = Arrangement.spacedBy(PageTokens.rowGap),
    ) {
        AddChip("Email") { onChange(draft.copy(emails = draft.emails + DraftValue())) }
        AddChip("Phone") { onChange(draft.copy(phones = draft.phones + DraftValue())) }
        if (draft.organization == null) AddChip("Organization") { onChange(draft.copy(organization = "")) }
        if (draft.title == null) AddChip("Title") { onChange(draft.copy(title = "")) }
        if (draft.birthday == null) AddChip("Birthday", onAddBirthday)
        if (draft.note == null) AddChip("Note") { onChange(draft.copy(note = "")) }
    }
}
