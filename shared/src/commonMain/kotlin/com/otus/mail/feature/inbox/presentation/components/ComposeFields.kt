package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.otus.designsystem.elements.ComposeField
import com.otus.designsystem.elements.ComposeValueField
import com.otus.designsystem.elements.IconButton
import com.otus.designsystem.elements.InputKind
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_expand_more
import com.otus.mail.feature.inbox.domain.recipientText
import com.otus.mail.feature.inbox.presentation.ComposeState
import com.otus.mail.feature.inbox.presentation.RecipientField

/**
 * The header lines of a draft: From, To, Cc, Bcc, and Subject. Cc and Bcc stay behind a button
 * on the To line until the user asks for them or the draft already has copies. [onChooseFrom]
 * is null when there is nothing to choose. [focus] is the To line's, for a page that starts there.
 */
@Composable
internal fun ComposeFields(
    state: ComposeState,
    onChooseFrom: (() -> Unit)?,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    var copies by remember { mutableStateOf(draft.cc.isNotEmpty() || draft.bcc.isNotEmpty()) }
    Column(modifier) {
        draft.from?.let { ComposeValueField("From", it.recipientText(), onChooseFrom) }
        RecipientLine(state, RecipientField.To, Modifier.focusRequester(focus)) {
            if (!copies) {
                IconButton(
                    "Show Cc and Bcc",
                    { copies = true },
                    outlined = false,
                ) { Symbol(Res.drawable.symbol_expand_more) }
            }
        }
        if (copies) {
            RecipientLine(state, RecipientField.Cc)
            RecipientLine(state, RecipientField.Bcc)
        }
        ComposeField("Subject", draft.subject, { subject -> state.edit { it.copy(subject = subject) } })
    }
}

@Composable
private fun RecipientLine(
    state: ComposeState,
    field: RecipientField,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    ComposeField(
        field.name,
        state.recipientText.getValue(field),
        { state.editRecipients(field, it) },
        modifier,
        kind = InputKind.Email,
        trailing = trailing,
    )
}
