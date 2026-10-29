package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_schedule_send
import com.otus.designsystem.shared.actionbar.components.BarContext
import com.otus.designsystem.shared.actionbar.components.BarToken
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.BarButton
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.barLayout
import com.otus.mail.feature.inbox.presentation.recipients

/**
 * The compose page's bar as [behavior] lays it out. Send runs [onSend], and stays disabled while that is null;
 * Attach runs [onAttach], disabled while that is null, and actions for messages, which Fixed mode may
 * place here, do not apply. The main button reads Send to, or Sends to with the schedule-send
 * symbol while [scheduled], and names the draft's recipients once it has any. A minimized bar
 * shows only the main button.
 */
@Composable
internal fun ComposeBar(
    behavior: MailBehavior,
    draft: Draft,
    onSend: (() -> Unit)?,
    onAttach: (() -> Unit)?,
    onDiscard: () -> Unit,
    scheduled: Boolean = false,
) {
    val offer = { action: BarButton ->
        val run =
            when (action) {
                MailAction.Send -> onSend
                MailAction.Attach -> onAttach
                MailAction.Discard -> onDiscard
                else -> null
            }
        val symbol = if (action == MailAction.Send && scheduled) Res.drawable.symbol_schedule_send else action.symbol
        run?.let { OfferedAction(action.buttonLabel(), symbol, leaves = true, run = it) }
    }
    MailActionBar(
        behavior.barLayout(ActionBarPlace.Compose),
        offer,
        onAction = { it.run() },
        minimized = Minimized().takeIf { behavior.minimizedBar },
        everyAction = behavior.everyAction(ActionBarPlace.Compose, offer),
        context = draft.recipients.barContext(if (scheduled) "Sends to" else "Send to"),
    )
}

/** [lead], "Send to" or "Sends to", above "Grace" with her avatar; the first two avatars and a count for several. */
private fun List<MailSender>.barContext(lead: String): BarContext? {
    val first = firstOrNull() ?: return null
    if (size > 1) return groupContext(this, lead)
    val name = first.shownName
    return BarContext(name.substringBefore(' '), BarToken.Person(first.chipAvatar), "to $name", lead = lead)
}
