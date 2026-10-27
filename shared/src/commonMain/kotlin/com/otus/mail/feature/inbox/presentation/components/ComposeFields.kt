package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.otus.designsystem.mail.compose.components.AddressInput
import com.otus.designsystem.mail.compose.components.RecipientLine
import com.otus.designsystem.mail.compose.elements.ComposeField
import com.otus.designsystem.shared.people.components.PersonAvatar
import com.otus.designsystem.shared.people.components.Recipient
import com.otus.designsystem.shared.selection.components.Dropdown
import com.otus.designsystem.shared.selection.components.DropdownRow
import com.otus.designsystem.theme.ComposeTokens
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.presentation.ComposeState
import com.otus.mail.feature.inbox.presentation.RecipientField
import com.otus.mail.feature.inbox.presentation.recipients

/**
 * The header lines of a draft: To, then Cc and Bcc while [shown], then Subject. Each recipient line
 * shows chips and suggests people as the user types. [focus] is the To line's, for a page that
 * starts there.
 */
@Composable
internal fun ComposeFields(
    state: ComposeState,
    shown: Set<RecipientField>,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        RecipientField.entries
            .filter { it == RecipientField.To || it in shown }
            .forEach { field ->
                RecipientRow(state, field, if (field == RecipientField.To) Modifier.focusRequester(focus) else Modifier)
            }
        ComposeField(
            "Subject",
            state.draft.subject,
            { subject -> state.edit { it.copy(subject = subject) } },
        )
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
            collapsedAfter = FOLDED_RECIPIENTS,
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
                        detail = person.address.takeIf { person.name.isNotBlank() },
                    ) { PersonAvatar(person.chipAvatar(), ComposeTokens.dropdownAvatarSize) }
                }
            }
        }
    }
}

private fun MailSender.recipient(): Recipient =
    Recipient(name.ifBlank { address }, chipAvatar(), if (name.isBlank()) address else "$name, $address")

internal fun MailSender.chipAvatar() = chipAvatar(avatar, name, address)

/** How many people a long recipient line shows before "+3 more". */
private const val FOLDED_RECIPIENTS = 2
