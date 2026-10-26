package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.ClosableSheet
import com.otus.designsystem.components.ContactRow
import com.otus.designsystem.elements.SectionCaption
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.Participant
import com.otus.mail.feature.inbox.domain.ParticipantRole

/**
 * Everyone a conversation names besides the user, under who wrote, who it went to, and who was
 * copied, so a long Cc list stays easy to scan. Tapping a person opens [onPick], where they can be
 * saved to contacts or opened.
 */
@Composable
internal fun PeopleSheet(
    people: List<Participant>,
    onPick: (MailSender) -> Unit,
    onDismiss: () -> Unit,
) {
    ClosableSheet("People", onDismiss) {
        // A long Cc list can name hundreds of people, so only the rows on screen are composed.
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            people.groupBy { it.role }.forEach { (role, group) ->
                item(role) { SectionCaption(role.title) }
                items(group, key = { it.person.address.lowercase() }) { participant ->
                    val person = participant.person
                    val name = person.name.ifBlank { person.address }
                    ContactRow(name, person.address, chipAvatar(null, name, person.address), { onPick(person) })
                }
            }
        }
    }
}

private val ParticipantRole.title: String
    get() =
        when (this) {
            ParticipantRole.Sender -> "Wrote"
            ParticipantRole.Recipient -> "To"
            ParticipantRole.Copied -> "Cc"
        }
