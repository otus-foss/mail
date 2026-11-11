package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.shared.actionbar.components.BarContext
import com.otusfoss.core.designsystem.shared.actionbar.components.BarToken
import com.otusfoss.mail.core.messages.domain.ActionBarPlace
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.presentation.MessageActions
import com.otusfoss.mail.core.messages.presentation.actions.ActionSubject
import com.otusfoss.mail.core.messages.presentation.actions.MoveOptions
import com.otusfoss.mail.core.messages.presentation.actions.barFor
import com.otusfoss.mail.core.messages.presentation.actions.offer
import com.otusfoss.mail.core.messages.presentation.actions.showsSuggestedMove
import com.otusfoss.mail.core.messages.presentation.components.MailActionBar
import com.otusfoss.mail.core.messages.presentation.components.Minimized
import com.otusfoss.mail.core.messages.presentation.components.rememberMoveChoice
import com.otusfoss.mail.core.messages.presentation.components.rememberSuggestedMove

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
    val saved = actions.saved()
    val role = actions.cleanup?.listRole?.invoke()
    val suggestion =
        rememberSuggestedMove(selected, actions, saved.showsSuggestedMove(ActionBarPlace.Selection, role))
    val subject = ActionSubject(actions, selected, moves = MoveOptions(moveChoice::choose, suggestion))
    val bar = saved.barFor(ActionBarPlace.Selection, role, subject::offer)
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
