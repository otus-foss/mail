package com.otusfoss.mail.feature.compose.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.otusfoss.core.designsystem.mail.compose.components.AddressInput
import com.otusfoss.core.designsystem.mail.compose.components.RecipientLine
import com.otusfoss.core.designsystem.mail.compose.elements.ComposeField
import com.otusfoss.core.designsystem.shared.people.components.PersonAvatar
import com.otusfoss.core.designsystem.shared.people.components.Recipient
import com.otusfoss.core.designsystem.shared.selection.components.Dropdown
import com.otusfoss.core.designsystem.shared.selection.components.PersonPickRow
import com.otusfoss.core.designsystem.shared.selection.components.PickMark
import com.otusfoss.core.designsystem.shared.selection.components.PickStyle
import com.otusfoss.core.designsystem.theme.tokens.people.AvatarTokens
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.shownName
import com.otusfoss.mail.core.messages.presentation.components.chipAvatar
import com.otusfoss.mail.feature.compose.presentation.ComposeState
import com.otusfoss.mail.feature.compose.presentation.RecipientField
import com.otusfoss.mail.feature.compose.presentation.recipients

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
                    SuggestedRecipient(person) { recipients.choose(field, person) }
                }
            }
        }
    }
}

@Composable
private fun SuggestedRecipient(
    person: MailSender,
    onSelect: () -> Unit,
) {
    PersonPickRow(
        person.shownName,
        person.address.takeIf { person.name.isNotBlank() },
        PickMark(false, style = PickStyle.Card),
        onSelect,
    ) { PersonAvatar(person.chipAvatar, AvatarTokens.menuSize) }
}

private fun MailSender.recipient(): Recipient =
    Recipient(shownName, chipAvatar, if (name.isBlank()) address else "$name, $address")

/** How many people a long recipient line shows before "+3 more". */
private const val FOLDED_RECIPIENTS = 2
