package com.otusfoss.mail.feature.contacts.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otusfoss.core.designsystem.shared.controls.elements.UiAction
import com.otusfoss.core.designsystem.shared.overlays.components.SheetFrame
import com.otusfoss.core.designsystem.shared.overlays.elements.sheetInset
import com.otusfoss.core.designsystem.shared.people.components.PersonAvatar
import com.otusfoss.core.designsystem.shared.selection.components.PickMark
import com.otusfoss.core.designsystem.shared.selection.components.PickRow
import com.otusfoss.core.designsystem.shared.selection.components.PickerSearch
import com.otusfoss.core.designsystem.theme.tokens.people.AvatarTokens
import com.otusfoss.mail.feature.contacts.domain.CardKey
import com.otusfoss.mail.feature.contacts.domain.ContactPerson
import com.otusfoss.mail.feature.contacts.presentation.avatar
import com.otusfoss.mail.feature.contacts.presentation.peopleCount

/**
 * Picks people to add to a group: everyone in [people] who is not in it yet, searchable by name
 * or address, any number at once. Add confirms them through [onAdd].
 */
@Composable
internal fun AddPeopleSheet(
    people: List<ContactPerson>,
    onAdd: (List<ContactPerson>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(emptySet<CardKey>()) }
    val chosen = people.filter { it.main.key in picked }
    SheetFrame(
        "Add people",
        onDismiss,
        done = UiAction("Add ${peopleCount(chosen.size)}", enabled = chosen.isNotEmpty(), onClick = { onAdd(chosen) }),
        closeButton = true,
    ) {
        PickerSearch(query, { query = it }, "Search people…", Modifier.sheetInset())
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            val shown = people.filter { query.isBlank() || it.matches(query) }
            items(shown, key = { it.main.key.cardId + it.main.key.accountId }) { person ->
                val key = person.main.key
                PickRow(
                    person.name,
                    person.emails.firstOrNull()?.value,
                    PickMark(key in picked, multiple = true),
                    { picked = if (key in picked) picked - key else picked + key },
                ) { PersonAvatar(person.avatar, AvatarTokens.size) }
            }
        }
    }
}
