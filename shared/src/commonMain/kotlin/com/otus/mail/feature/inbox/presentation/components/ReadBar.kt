package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.OwnAddresses
import com.otus.mail.feature.inbox.domain.replyAction
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.actions.ActionSubject
import com.otus.mail.feature.inbox.presentation.actions.MailBar
import com.otus.mail.feature.inbox.presentation.actions.OfferedAction
import com.otus.mail.feature.inbox.presentation.actions.barFor
import com.otus.mail.feature.inbox.presentation.actions.offer
import com.otus.mail.feature.inbox.presentation.actions.showsSuggestedMove

/**
 * What the Read view's actions work on: filing acts on the [row], the whole conversation, while
 * replies follow the [page] on screen. The [bar] is null without [ActionSubject.actions]; then
 * only replies are offered, from the ⋯ menu.
 */
internal class ReadActions(
    val subject: ActionSubject,
    val bar: MailBar?,
)

/** The Read view's actions, with the bar's big button replying to all of the [page]'s people or to its sender. */
@Composable
internal fun rememberReadActions(
    row: MailMessage,
    page: MailMessage,
    own: OwnAddresses,
    actions: MessageActions?,
    moveChoice: MoveChoice,
    onCompose: (ComposeRequest) -> Unit,
): ReadActions {
    val role = actions?.cleanup?.role?.invoke(row)
    val behavior = actions?.behavior()
    val wanted = behavior?.showsSuggestedMove(ActionBarPlace.Message, role) == true
    val suggestion = rememberSuggestedMove(listOf(row), actions, wanted)
    val subject =
        ActionSubject(
            actions,
            listOf(row),
            chooseFolder = moveChoice::choose,
            suggestion = suggestion,
            page = page,
            role = role,
            onCompose = onCompose,
        )
    return ReadActions(subject, behavior?.barFor(ActionBarPlace.Message, role, subject::offer, page.replyAction(own)))
}

/** The [bar], with the big button saying who the reply to the [page] goes to. */
@Composable
internal fun ReadActionBar(
    bar: MailBar,
    page: MailMessage,
    onAction: (OfferedAction) -> Unit,
) {
    MailActionBar(bar, onAction, context = replyContext(page, bar.layout.primary))
}
