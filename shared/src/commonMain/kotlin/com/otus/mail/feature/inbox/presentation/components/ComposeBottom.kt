package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.otus.designsystem.mail.compose.components.CompactSendButton
import com.otus.designsystem.mail.compose.components.FormatButton
import com.otus.designsystem.mail.compose.components.FormattingBar
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_attach_file
import com.otus.designsystem.resources.symbol_schedule
import com.otus.designsystem.resources.symbol_schedule_send
import com.otus.designsystem.resources.symbol_send
import com.otus.designsystem.shared.actionbar.components.BarMorph
import com.otus.designsystem.shared.actionbar.components.BarNotice
import com.otus.designsystem.theme.tokens.overlays.ActionBarTokens
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.shownName
import com.otus.mail.feature.inbox.presentation.ComposeState
import com.otus.mail.feature.inbox.presentation.recipients
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * The compose page's bottom: its action bar, or while the [keyboardUp] the formatting row with
 * Attach and a compact Send. While a time is scheduled, a notice above it names the time, and
 * Change runs [onChangeSchedule]. A reply's earlier messages sit above the bar and open the
 * thread's sheet. Send runs [onSend] and is disabled while it is null; Attach picks files, and is
 * disabled where the platform cannot pick them.
 */
@Composable
internal fun ComposeBottom(
    state: ComposeState,
    behavior: MailBehavior,
    keyboardUp: Boolean,
    onSend: (() -> Unit)?,
    onDiscard: () -> Unit,
    onChangeSchedule: () -> Unit,
) {
    var linking by remember { mutableStateOf(false) }
    val now = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }
    val at = state.sendAt?.sendLaterLabel(now)
    var showingThread by remember { mutableStateOf(false) }
    val scheduled = state.sendAt
    val thread = state.thread
    Column(verticalArrangement = Arrangement.spacedBy(ActionBarTokens.gap)) {
        if (scheduled != null) {
            BarNotice(Res.drawable.symbol_schedule, scheduled.scheduledLabel(), "Change", onChangeSchedule)
        }
        if (thread != null && !keyboardUp) ReplyThreadStrip(thread) { showingThread = true }
        Bottom(state, behavior, keyboardUp, onSend, onDiscard, at) { linking = true }
    }
    if (linking) LinkSheet(state.body::addLink) { linking = false }
    if (showingThread && thread != null) ThreadSheet(thread) { showingThread = false }
    val question = state.attachments.cleaning.question
    if (question != null) PhotoCleaningSheet(question, state.attachments.cleaning::answer)
}

/** The action bar, the formatting row while the keyboard is up, or the image bar; each morphs into the next. */
@Composable
private fun Bottom(
    state: ComposeState,
    behavior: MailBehavior,
    keyboardUp: Boolean,
    onSend: (() -> Unit)?,
    onDiscard: () -> Unit,
    at: String?,
    onLink: () -> Unit,
) {
    val shown =
        when {
            state.body.images.selected != null -> BottomBar.Image
            keyboardUp -> BottomBar.Formatting
            else -> BottomBar.Actions
        }
    BarMorph(shown) { bar ->
        when (bar) {
            BottomBar.Image -> ComposeImageBar(state)
            BottomBar.Formatting -> FormattingRow(state, onSend, at, onLink)
            BottomBar.Actions -> DraftActions(state, behavior, onSend, onDiscard, scheduled = at != null)
        }
    }
}

@Composable
private fun DraftActions(
    state: ComposeState,
    behavior: MailBehavior,
    onSend: (() -> Unit)?,
    onDiscard: () -> Unit,
    scheduled: Boolean,
) {
    ComposeBar(
        behavior,
        state.draft,
        onSend = onSend,
        onAttach = state.attachments::attach.takeIf { state.attachments.canAttach },
        onDiscard = onDiscard,
        scheduled = scheduled,
    )
}

/** Which bar the compose page shows: the image's while one is selected, the formatting row while typing. */
private enum class BottomBar { Image, Formatting, Actions }

/** The formatting row with Attach and a compact Send, while the keyboard is up. */
@Composable
private fun FormattingRow(
    state: ComposeState,
    onSend: (() -> Unit)?,
    at: String?,
    onLink: () -> Unit,
) {
    FormattingBar(
        state.body,
        onLink = onLink,
        leading = {
            FormatButton(
                "Attach",
                Res.drawable.symbol_attach_file,
                active = false,
                onClick = state.attachments::attach,
                enabled = state.attachments.canAttach,
            )
        },
        trailing = {
            CompactSendButton(
                sendDescription(state, at),
                { onSend?.invoke() },
                enabled = onSend != null,
                icon = if (at == null) Res.drawable.symbol_send else Res.drawable.symbol_schedule_send,
            )
        },
    )
}

/** What screen readers hear for the compact Send, such as "Send Sun, 8:00, to Grace". */
private fun sendDescription(
    state: ComposeState,
    at: String?,
): String {
    val people = state.draft.recipients
    val send = at?.let { "Send $it," } ?: "Send"
    val first = people.firstOrNull() ?: return send
    return if (people.size == 1) "$send to ${first.shownName}" else "$send to ${people.size} people"
}
