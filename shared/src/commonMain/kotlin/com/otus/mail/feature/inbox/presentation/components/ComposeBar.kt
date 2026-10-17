package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.BarContext
import com.otus.designsystem.components.BarToken
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_schedule_send
import com.otus.mail.feature.inbox.domain.ActionBarLayout
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.presentation.recipients

/**
 * The compose page's bar for [layout]. Send runs [onSend], and stays disabled while that is null;
 * Attach stays disabled until Mail has attachments, and actions for messages, which Fixed mode may
 * place here, do not apply. The main button reads Send to, or Sends to with the schedule-send
 * symbol while [scheduled], and names the draft's recipients once it has any. A
 * [minimized] bar shows only the main button.
 */
@Composable
internal fun ComposeBar(
    layout: ActionBarLayout,
    draft: Draft,
    onSend: (() -> Unit)?,
    onDiscard: () -> Unit,
    minimized: Boolean,
    modifier: Modifier = Modifier,
    scheduled: Boolean = false,
) {
    MailActionBar(
        layout,
        offer = { action ->
            val run =
                when (action) {
                    MailAction.Send -> onSend
                    MailAction.Discard -> onDiscard
                    else -> null
                }
            val symbol =
                if (action == MailAction.Send &&
                    scheduled
                ) {
                    Res.drawable.symbol_schedule_send
                } else {
                    action.symbol
                }
            run?.let { OfferedAction(action.buttonLabel(), symbol, leaves = true, run = it) }
        },
        onAction = { it.run() },
        minimized = minimized,
        modifier = modifier,
        context = draft.recipients.barContext(if (scheduled) "Sends to" else "Send to"),
    )
}

/** [lead], "Send to" or "Sends to", above "Grace" with her avatar; the first two avatars and a count for several. */
private fun List<MailSender>.barContext(lead: String): BarContext? {
    val first = firstOrNull() ?: return null
    val avatar = chipAvatar(first.avatar, first.name, first.address)
    val name = first.name.ifBlank { first.address }
    return if (size == 1) {
        BarContext(name.substringBefore(' '), BarToken.Person(avatar), "to $name", lead = lead)
    } else {
        val second = this[1].let { chipAvatar(it.avatar, it.name, it.address) }
        BarContext("$size people", BarToken.People(avatar, size, second), "to $size people", lead = lead)
    }
}
