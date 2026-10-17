package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.otus.designsystem.components.CompactSendButton
import com.otus.designsystem.components.FormatButton
import com.otus.designsystem.components.FormattingBar
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_attach_file
import com.otus.designsystem.theme.MotionCurve
import com.otus.designsystem.theme.MotionSpeed
import com.otus.designsystem.theme.motionTween
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.presentation.ComposeState
import com.otus.mail.feature.inbox.presentation.recipients

/**
 * The compose page's bottom: its action bar, or while the [keyboardUp] the formatting row with
 * Attach and a compact Send. They fade into each other. Send runs [onSend] and is disabled while
 * it is null; Attach stays disabled until Mail has attachments.
 */
@Composable
internal fun ComposeBottom(
    state: ComposeState,
    behavior: MailBehavior,
    keyboardUp: Boolean,
    onSend: (() -> Unit)?,
    onDiscard: () -> Unit,
) {
    var linking by remember { mutableStateOf(false) }
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
                onLink = { linking = true },
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
                        sendDescription(state),
                        { onSend?.invoke() },
                        enabled = onSend != null,
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
            )
        }
    }
    if (linking) LinkDialog(state.body::addLink) { linking = false }
}

private fun sendDescription(state: ComposeState): String {
    val people = state.draft.recipients
    val first = people.firstOrNull() ?: return "Send"
    return if (people.size == 1) "Send to ${first.name.ifBlank { first.address }}" else "Send to ${people.size} people"
}
