package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.BarContext
import com.otus.designsystem.components.BarToken
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.BarButton
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.barLayout
import com.otus.mail.feature.inbox.presentation.MessageActions

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
    val layout = behavior.barLayout(ActionBarPlace.Selection, role)
    val suggestion = rememberSuggestedMove(selected, actions, wanted = MailAction.SuggestedMove in layout.buttons)
    val offer = { button: BarButton -> actions.offer(selected, button, moveChoice::choose, suggestion) }
    MailActionBar(
        layout,
        offer = offer,
        onAction = { action ->
            action.run()
            if (!action.asks) onDone()
        },
        minimized =
            Minimized(
                more = behavior.overflow(ActionBarPlace.Selection, offer, role),
            ).takeIf { behavior.minimizedBar },
        context = selectionContext(selected.size),
    )
    moveChoice.Sheet(actions)
}

private fun selectionContext(count: Int): BarContext {
    val noun = if (count == 1) "message" else "messages"
    return BarContext(noun, BarToken.Count(count), description = "$count $noun")
}
