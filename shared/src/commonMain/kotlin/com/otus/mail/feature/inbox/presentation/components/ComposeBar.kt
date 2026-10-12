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
 * The compose page's bar for [layout]. Only Discard runs; Send and Attach stay disabled until
 * Mail can send, and actions for messages, which Fixed mode may place here, do not apply. The
 * main button names the draft's recipients once it has any.
 */
@Composable
internal fun ComposeBar(
    layout: ActionBarLayout,
    draft: Draft,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MailActionBar(
        layout,
        offer = { action ->
            OfferedAction(action.buttonLabel, action.symbol, leaves = true, run = onDiscard).takeIf {
                action ==
                    MailAction.Discard
            }
        },
        onAction = { it.run() },
        modifier = modifier,
        context = draft.recipients.barContext(),
    )
}

/** "to Ada" with her avatar for one recipient; the first one's avatar and a count for several. */
private fun List<MailSender>.barContext(): BarContext? {
    val first = firstOrNull() ?: return null
    val avatar = chipAvatar(first.avatar, first.name, first.address)
    return if (size == 1) {
        BarContext("to ${first.name.ifBlank { first.address }}", BarToken.Person(avatar))
    } else {
        BarContext("$size people", BarToken.People(avatar, size))
    }
}
