package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.BarContext
import com.otus.designsystem.components.BarToken
import com.otus.mail.feature.inbox.domain.ActionBarLayout
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.presentation.recipients

/**
 * The compose page's bar for [layout]. Send runs [onSend], and stays disabled while that is null;
 * Attach stays disabled until Mail has attachments, and actions for messages, which Fixed mode may
 * place here, do not apply. The main button reads Send to and names the draft's recipients once it has any. A
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
            run?.let { OfferedAction(action.buttonLabel(), action.symbol, leaves = true, run = it) }
        },
        onAction = { it.run() },
        minimized = minimized,
        modifier = modifier,
        context = draft.recipients.barContext(),
    )
}

/** "Send to" above "Grace" with her avatar for one recipient; the first one's avatar and a count for several. */
private fun List<MailSender>.barContext(): BarContext? {
    val first = firstOrNull() ?: return null
    val avatar = chipAvatar(first.avatar, first.name, first.address)
    val name = first.name.ifBlank { first.address }
    return if (size == 1) {
        BarContext(name.substringBefore(' '), BarToken.Person(avatar), "to $name", lead = "Send to")
    } else {
        BarContext("$size people", BarToken.People(avatar, size), "to $size people", lead = "Send to")
    }
}
