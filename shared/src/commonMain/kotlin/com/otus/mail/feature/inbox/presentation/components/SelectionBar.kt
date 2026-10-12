package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.BarContext
import com.otus.designsystem.components.BarToken
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.MessageActions

/**
 * The bar for [selected] messages, which names their number beside the primary. An action they
 * cannot take stays in its slot, disabled. Each action, or a folder chosen to move them to, ends
 * the selection.
 */
@Composable
internal fun SelectionBar(
    selected: List<MailMessage>,
    actions: MessageActions,
    onDone: () -> Unit,
) {
    val moveChoice = rememberMoveChoice(onDone)
    val behavior = actions.behavior()
    MailActionBar(
        behavior.barLayout(ActionBarPlace.Selection),
        offer = { actions.offer(selected, it, moveChoice::choose) },
        onAction = { action ->
            action.run()
            if (!action.asks) onDone()
        },
        minimized = behavior.minimizedBar,
        context = selectionContext(selected.size),
    )
    moveChoice.Sheet(actions)
}

private fun selectionContext(count: Int): BarContext {
    val noun = if (count == 1) "message" else "messages"
    return BarContext(noun, BarToken.Count(count), description = "$count $noun")
}
