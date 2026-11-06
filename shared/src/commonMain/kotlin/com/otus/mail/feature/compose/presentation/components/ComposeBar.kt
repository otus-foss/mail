package com.otus.mail.feature.compose.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.shared.actionbar.components.BarContext
import com.otus.designsystem.shared.actionbar.components.BarToken
import com.otus.mail.core.messages.domain.ActionBarPlace
import com.otus.mail.core.messages.domain.Draft
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.shownName
import com.otus.mail.core.messages.presentation.actions.ActionSubject
import com.otus.mail.core.messages.presentation.actions.DraftSubject
import com.otus.mail.core.messages.presentation.actions.barFor
import com.otus.mail.core.messages.presentation.actions.offer
import com.otus.mail.core.messages.presentation.components.MailActionBar
import com.otus.mail.core.messages.presentation.components.chipAvatar
import com.otus.mail.core.messages.presentation.components.groupContext
import com.otus.mail.core.settings.domain.MailBehavior
import com.otus.mail.feature.compose.presentation.recipients

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
    val subject = ActionSubject(actions = null, draft = DraftSubject(onSend, onAttach, onDiscard, scheduled))
    MailActionBar(
        behavior.barFor(ActionBarPlace.Compose, role = null, subject::offer),
        onAction = { it.run() },
        context = draft.recipients.barContext(if (scheduled) "Sends to" else "Send to"),
    )
}

/** [lead], "Send to" or "Sends to", above "Grace" with her avatar; the first two avatars and a count for several. */
private fun List<MailSender>.barContext(lead: String): BarContext? {
    val first = firstOrNull() ?: return null
    val name = first.shownName
    return if (size == 1) {
        BarContext(name.substringBefore(' '), BarToken.Person(first.chipAvatar), "to $name", lead = lead)
    } else {
        groupContext(this, lead)
    }
}
