package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.otus.designsystem.components.ActionBarHost
import com.otus.designsystem.components.ConfirmDialog
import com.otus.designsystem.components.OptionsItem
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_delete
import com.otus.designsystem.resources.symbol_signature
import com.otus.designsystem.resources.symbol_visibility_off
import com.otus.designsystem.theme.InboxTokens
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.PageTokens
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.presentation.ComposeState
import com.otus.mail.feature.inbox.presentation.RecipientField
import com.otus.mail.feature.inbox.presentation.components.ComposeBottom
import com.otus.mail.feature.inbox.presentation.components.ComposeContent
import com.otus.mail.feature.inbox.presentation.components.ComposePanel
import com.otus.mail.feature.inbox.presentation.recipients

/**
 * A new message, reply, or forward as a page. Leaving it by Back or the chevron runs [onClose]
 * and saves the draft if the user wrote anything. Send closes the page once the server accepts
 * the message, after asking about a missing subject; a failed send stays open with the reason
 * under the header. Discard stops saving and passes [onDiscard] what deletes the saved copy, to run
 * once Undo has passed. While the keyboard is up, a formatting row takes the action bar's place.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComposeScreen(
    state: ComposeState,
    behavior: MailBehavior,
    onClose: () -> Unit,
    onDiscard: (delete: () -> Unit) -> Unit,
) {
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        onBackCompleted = onClose,
    )
    LaunchedEffect(state) { state.loadIdentities() }
    LaunchedEffect(state) { state.followBody() }
    // Leaving the page by any route, Back or another page, saves what is new.
    DisposableEffect(state) { onDispose { state.close() } }
    val draft = state.draft
    var shown by remember {
        mutableStateOf(
            RecipientField.entries.filter { draft.recipients(it).isNotEmpty() }.toSet(),
        )
    }
    var askingSubject by remember { mutableStateOf(false) }
    val discard = { onDiscard(state.discard()) }
    val send = { if (draft.subject.isBlank()) askingSubject = true else state.send(onClose) }
    Surface(Modifier.fillMaxSize(), color = OtusColors.bgRegular) {
        ActionBarHost(
            bar = {
                ComposeBottom(
                    state,
                    behavior,
                    keyboardUp = WindowInsets.isImeVisible,
                    onSend = send.takeIf { state.canSend },
                    onDiscard = discard,
                )
            },
            Modifier.safeDrawingPadding(),
        ) { padding ->
            ComposePage(
                state,
                behavior,
                shown,
                onShownChange = { shown = it },
                onClose = onClose,
                onDiscard = discard,
                padding = padding,
            )
        }
    }
    if (askingSubject) SubjectPrompt({ state.send(onClose) }) { askingSubject = false }
}

/** The panel, any notice, and the page's lines and body; [shown] says which copy lines show. */
@Composable
private fun ComposePage(
    state: ComposeState,
    behavior: MailBehavior,
    shown: Set<RecipientField>,
    onShownChange: (Set<RecipientField>) -> Unit,
    onClose: () -> Unit,
    onDiscard: () -> Unit,
    padding: PaddingValues,
) {
    Column(Modifier.fillMaxSize().padding(PageTokens.pageInset)) {
        ComposePanel(
            state,
            state.request.title,
            behavior.recentFrom,
            onBack = onClose,
            onAddCc = { onShownChange(shown + RecipientField.Cc) }.takeIf { RecipientField.Cc !in shown },
            options =
                options(
                    state,
                    RecipientField.Bcc in shown,
                    { onShownChange(shown.toggled(RecipientField.Bcc, state)) },
                    onDiscard,
                ),
        )
        state.notice?.let {
            Text(
                it,
                Modifier.padding(horizontal = PageTokens.textInset),
                color = OtusColors.text2,
                fontSize = InboxTokens.metadataSize,
            )
        }
        ComposeContent(state, shown, Modifier.weight(1f), padding)
    }
}

@Composable
private fun SubjectPrompt(
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        "Send without a subject?",
        "The message goes out with an empty subject line.",
        "Send",
        onConfirm = onSend,
        onDismiss = onDismiss,
        dismissLabel = "Add subject",
    )
}

/** The ⋯ menu: show or hide Bcc, the signature switch, then discard the draft. */
private fun options(
    state: ComposeState,
    bcc: Boolean,
    onToggleBcc: () -> Unit,
    onDiscard: () -> Unit,
): List<OptionsItem> =
    listOf(
        OptionsItem.Action(if (bcc) "Remove Bcc" else "Add Bcc", Res.drawable.symbol_visibility_off, onToggleBcc),
        OptionsItem.Toggle("Signature", Res.drawable.symbol_signature, state.signatureOn, state::showSignature),
        OptionsItem.Divider,
        OptionsItem.Action("Discard draft", Res.drawable.symbol_delete, onDiscard),
    )

/** Shows [field], or hides it and drops its people from the draft. */
private fun Set<RecipientField>.toggled(
    field: RecipientField,
    state: ComposeState,
): Set<RecipientField> {
    if (field !in this) return this + field
    state.recipients.clear(field)
    return this - field
}

private val ComposeRequest.title: String
    get() =
        when (this) {
            is ComposeRequest.New -> "New message"
            is ComposeRequest.Reply -> if (all) "Reply all" else "Reply"
            is ComposeRequest.FollowUp -> "Follow up"
            is ComposeRequest.Forward -> "Forward"
            is ComposeRequest.Resume -> from.title
        }
