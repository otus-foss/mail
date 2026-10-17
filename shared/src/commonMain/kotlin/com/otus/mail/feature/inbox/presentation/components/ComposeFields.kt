package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.otus.designsystem.components.AddressInput
import com.otus.designsystem.components.Dropdown
import com.otus.designsystem.components.DropdownRow
import com.otus.designsystem.components.PersonAvatar
import com.otus.designsystem.components.Recipient
import com.otus.designsystem.components.RecipientLine
import com.otus.designsystem.elements.ComposeChoiceField
import com.otus.designsystem.elements.ComposeField
import com.otus.designsystem.theme.ComposeTokens
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.presentation.ComposeState
import com.otus.mail.feature.inbox.presentation.RecipientField
import com.otus.mail.feature.inbox.presentation.recipients
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * The header lines of a draft: To, then Cc and Bcc while shown, then Subject, then when it sends
 * once Schedule send picked a time. Each recipient line
 * shows chips and suggests people as the user types. [focus] is the To line's, for a page that
 * starts there.
 */
@Composable
internal fun ComposeFields(
    state: ComposeState,
    lines: ComposeLines,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        RecipientField.entries
            .filter { it == RecipientField.To || it in lines.shown }
            .forEach { field ->
                RecipientRow(state, field, if (field == RecipientField.To) Modifier.focusRequester(focus) else Modifier)
            }
        ComposeField(
            "Subject",
            state.draft.subject,
            { subject -> state.edit { it.copy(subject = subject) } },
            placeholder = "Subject",
        )
        state.sendAt?.let { at ->
            val now = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }
            ComposeChoiceField(
                "Send",
                at.sendLaterLabel(now),
                lines.onChangeSchedule,
                { state.sendAt = null },
                "Send now instead",
            )
        }
    }
}

@Composable
private fun RecipientRow(
    state: ComposeState,
    field: RecipientField,
    modifier: Modifier,
) {
    val recipients = state.recipients
    Box {
        RecipientLine(
            field.name,
            state.draft.recipients(field).map { it.recipient() },
            AddressInput(
                recipients.typed.getValue(field),
                onTextChange = { recipients.type(field, it) },
                onCommit = { recipients.commit(field) },
                onRemoveLast = { recipients.removeLast(field) },
            ),
            onChipClick = { recipients.edit(field, it) },
            modifier = modifier,
        )
        val suggested =
            recipients.suggestions
                .takeIf { it.field == field }
                ?.people
                .orEmpty()
        if (suggested.isNotEmpty()) {
            Dropdown(recipients::dismissSuggestions, focusable = false) {
                suggested.forEach { person ->
                    DropdownRow(
                        person.name.ifBlank { person.address },
                        selected = false,
                        onClick = { recipients.choose(field, person) },
                        subtitle = person.address.takeIf { person.name.isNotBlank() },
                    ) { PersonAvatar(person.chipAvatar(), ComposeTokens.dropdownAvatarSize) }
                }
            }
        }
    }
}

private fun MailSender.recipient(): Recipient =
    Recipient(name.ifBlank { address }, chipAvatar(), if (name.isBlank()) address else "$name, $address")

internal fun MailSender.chipAvatar() = chipAvatar(avatar, name, address)
