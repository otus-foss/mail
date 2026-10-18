package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import com.otus.designsystem.elements.isKeyboardUp
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_delete
import com.otus.designsystem.resources.symbol_schedule_send
import com.otus.designsystem.resources.symbol_signature
import com.otus.designsystem.resources.symbol_visibility_off
import com.otus.designsystem.theme.InboxTokens
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.PageTokens
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.presentation.ComposeExits
import com.otus.mail.feature.inbox.presentation.ComposeState
import com.otus.mail.feature.inbox.presentation.RecipientField
import com.otus.mail.feature.inbox.presentation.components.ComposeBottom
import com.otus.mail.feature.inbox.presentation.components.ComposeContent
import com.otus.mail.feature.inbox.presentation.components.ComposePanel
import com.otus.mail.feature.inbox.presentation.components.SendLaterSheet
import com.otus.mail.feature.inbox.presentation.recipients
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * A new message, reply, or forward as a page. Leaving it by Back or the chevron closes it through
 * [exits] and saves the draft if the user wrote anything. Schedule send only picks a time, which
 * shows above the bar; Send, after asking about a missing subject, hands the draft to [exits] for
 * that time or now and leaves the page. Discard stops saving and hands [exits] what deletes the
 * saved copy, to run once Undo has passed. While the keyboard is up, a formatting row takes the
 * action bar's place.
 */
@Composable
fun ComposeScreen(
    state: ComposeState,
    behavior: MailBehavior,
    exits: ComposeExits,
) {
    val onClose = exits::close
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        onBackCompleted = onClose,
    )
    LaunchedEffect(state) { state.loadIdentities() }
    LaunchedEffect(state) { state.followBody() }
    // Leaving the page by any route, Back or another page, saves what is new.
    DisposableEffect(state) { onDispose { state.close() } }
    val draft = state.draft
    var shown by remember { mutableStateOf(draft.copyLines()) }
    var askingSubject by remember { mutableStateOf(false) }
    var schedulingAt by remember { mutableStateOf(false) }
    val discard = { exits.discarded(state.discard()) }
    val sendNow = { at: LocalDateTime? -> state.handOff()?.let { exits.send(it, at) } }
    val send: () -> Unit = { if (draft.subject.isBlank()) askingSubject = true else sendNow(state.sendAt) }
    Surface(Modifier.fillMaxSize(), color = OtusColors.bgRegular) {
        ActionBarHost(
            bar = {
                ComposeBottom(
                    state,
                    behavior,
                    keyboardUp = isKeyboardUp(),
                    onSend = send.takeIf { state.canSend },
                    onDiscard = discard,
                    onChangeSchedule = { schedulingAt = true },
                )
            },
            Modifier.safeDrawingPadding(),
        ) { padding ->
            ComposePage(
                state,
                behavior,
                shown,
                onAddCc = { shown = shown + RecipientField.Cc }.takeIf { RecipientField.Cc !in shown },
                onClose = onClose,
                options =
                    options(
                        state,
                        RecipientField.Bcc in shown,
                        onSchedule = { schedulingAt = true },
                        onToggleBcc = { shown = shown.toggled(RecipientField.Bcc, state) },
                        onDiscard = discard,
                    ),
                padding = padding,
            )
        }
    }
    if (askingSubject) SubjectPrompt({ sendNow(state.sendAt) }) { askingSubject = false }
    if (schedulingAt) {
        SendLaterPrompt(
            state.maxDelaySeconds,
            state.sendAt,
            { state.sendAt = it },
        ) { schedulingAt = false }
    }
}

/** The panel, any notice, and the page's lines and body; [shown] says which copy lines show. */
@Composable
private fun ComposePage(
    state: ComposeState,
    behavior: MailBehavior,
    shown: Set<RecipientField>,
    onAddCc: (() -> Unit)?,
    onClose: () -> Unit,
    options: List<OptionsItem>,
    padding: PaddingValues,
) {
    Column(Modifier.fillMaxSize().padding(PageTokens.pageInset)) {
        ComposePanel(
            state,
            state.request.title,
            behavior.recentFrom,
            onBack = onClose,
            onAddCc = onAddCc,
            options = options,
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

/**
 * The Send later sheet from now until [maxDelaySeconds] ahead, in the device's time zone. Choosing
 * sets the time Send uses, [chosen] until then, or none for Send now.
 */
@Composable
private fun SendLaterPrompt(
    maxDelaySeconds: Long,
    chosen: LocalDateTime?,
    onChoose: (LocalDateTime?) -> Unit,
    onDismiss: () -> Unit,
) {
    val now = remember { Clock.System.now() }
    val zone = TimeZone.currentSystemDefault()
    SendLaterSheet(
        now.toLocalDateTime(zone),
        (now + maxDelaySeconds.seconds).toLocalDateTime(zone),
        chosen,
        onChoose,
        onDismiss,
    )
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

/** The ⋯ menu: Schedule send where the server can, Bcc, the signature switch, then discard. */
private fun options(
    state: ComposeState,
    bcc: Boolean,
    onSchedule: () -> Unit,
    onToggleBcc: () -> Unit,
    onDiscard: () -> Unit,
): List<OptionsItem> =
    listOfNotNull(
        OptionsItem
            .Action("Schedule send", Res.drawable.symbol_schedule_send, onSchedule, opens = true)
            .takeIf { state.maxDelaySeconds > 0 },
        OptionsItem.Action(if (bcc) "Remove Bcc" else "Add Bcc", Res.drawable.symbol_visibility_off, onToggleBcc),
        OptionsItem.Toggle("Signature", Res.drawable.symbol_signature, state.signatureOn, state::showSignature),
        OptionsItem.Divider,
        OptionsItem.Action("Discard draft", Res.drawable.symbol_delete, onDiscard),
    )

/** The copy lines a draft opens with: those that already hold people. */
private fun Draft.copyLines(): Set<RecipientField> =
    RecipientField.entries
        .filter {
            recipients(it).isNotEmpty()
        }.toSet()

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
