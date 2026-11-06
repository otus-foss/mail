package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.shared.actionbar.components.BarContext
import com.otus.designsystem.shared.actionbar.components.BarToken
import com.otus.mail.core.messages.domain.ActionBarPlace
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.presentation.MessageActions
import com.otus.mail.core.messages.presentation.actions.ActionSubject
import com.otus.mail.core.messages.presentation.actions.barFor
import com.otus.mail.core.messages.presentation.actions.offer
import com.otus.mail.core.messages.presentation.actions.showsSuggestedMove
import com.otus.mail.core.messages.presentation.components.MailActionBar
import com.otus.mail.core.messages.presentation.components.Minimized
import com.otus.mail.core.messages.presentation.components.rememberMoveChoice
import com.otus.mail.core.messages.presentation.components.rememberSuggestedMove

/**
 * The bar for [selected] messages, which names their number beside the primary. An action they
 * cannot take stays in its slot, disabled. Each action, or a folder chosen to move them to, ends
 * the selection. A minimized bar lists the actions it leaves out behind a round button.
 */
@Composable
internal fun SelectionBar(
    selected: List<MailMessage>,
    actions: MessageActions,
    onDone: () -> Unit,
) {
    val moveChoice = rememberMoveChoice(onDone)
    val behavior = actions.behavior()
    val role = actions.cleanup?.listRole?.invoke()
    val suggestion =
        rememberSuggestedMove(selected, actions, behavior.showsSuggestedMove(ActionBarPlace.Selection, role))
    val subject = ActionSubject(actions, selected, chooseFolder = moveChoice::choose, suggestion = suggestion)
    val bar = behavior.barFor(ActionBarPlace.Selection, role, subject::offer)
    MailActionBar(
        bar,
        onAction = { action ->
            action.run()
            if (!action.asks) onDone()
        },
        minimized = Minimized(more = bar.more()),
        context = selectionContext(selected.size),
    )
    moveChoice.Sheet(actions)
}

private fun selectionContext(count: Int): BarContext {
    val noun = if (count == 1) "message" else "messages"
    return BarContext(noun, BarToken.Count(count), description = "$count $noun")
}
