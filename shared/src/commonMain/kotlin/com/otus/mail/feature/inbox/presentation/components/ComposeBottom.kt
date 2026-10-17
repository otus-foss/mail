package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.otus.designsystem.components.BarNotice
import com.otus.designsystem.components.CompactSendButton
import com.otus.designsystem.components.FormatButton
import com.otus.designsystem.components.FormattingBar
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_attach_file
import com.otus.designsystem.resources.symbol_schedule
import com.otus.designsystem.resources.symbol_schedule_send
import com.otus.designsystem.resources.symbol_send
import com.otus.designsystem.theme.ActionBarTokens
import com.otus.designsystem.theme.MotionCurve
import com.otus.designsystem.theme.MotionSpeed
import com.otus.designsystem.theme.motionTween
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.presentation.ComposeState
import com.otus.mail.feature.inbox.presentation.recipients
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * The compose page's bottom: its action bar, or while the [keyboardUp] the formatting row with
 * Attach and a compact Send. While a time is scheduled, a notice above it names the time, and
 * Change runs [onChangeSchedule]. They fade into each other. Send runs [onSend] and is disabled while
 * it is null; Attach stays disabled until Mail has attachments.
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
    Column(verticalArrangement = Arrangement.spacedBy(ActionBarTokens.gap)) {
        state.sendAt?.let { BarNotice(Res.drawable.symbol_schedule, it.scheduledLabel(), "Change", onChangeSchedule) }
        Bottom(state, behavior, keyboardUp, onSend, onDiscard, at) { linking = true }
    }
    if (linking) LinkDialog(state.body::addLink) { linking = false }
}

/** The action bar, or the formatting row while the keyboard is up; they fade into each other. */
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
    val arrive = motionTween<Float>(MotionSpeed.Fast, MotionCurve.Enter)
    val leave = motionTween<Float>(MotionSpeed.Fast, MotionCurve.Exit)
    AnimatedContent(
        keyboardUp,
        transitionSpec = { fadeIn(arrive) togetherWith fadeOut(leave) },
        label = "Compose bottom",
    ) { up ->
        if (up) {
            FormattingBar(
                state.body,
                onLink = onLink,
                leading = {
                    FormatButton(
                        "Attach",
                        Res.drawable.symbol_attach_file,
                        active = false,
                        onClick = {},
                        enabled = false,
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
        } else {
            ComposeBar(
                behavior.barLayout(ActionBarPlace.Compose),
                state.draft,
                onSend = onSend,
                onDiscard = onDiscard,
                minimized = behavior.minimizedBar,
                scheduled = at != null,
            )
        }
    }
}

/** What screen readers hear for the compact Send, such as "Send Sun, 8:00, to Grace". */
private fun sendDescription(
    state: ComposeState,
    at: String?,
): String {
    val people = state.draft.recipients
    val send = at?.let { "Send $it," } ?: "Send"
    val first = people.firstOrNull() ?: return send
    return if (people.size ==
        1
    ) {
        "$send to ${first.name.ifBlank { first.address }}"
    } else {
        "$send to ${people.size} people"
    }
}
