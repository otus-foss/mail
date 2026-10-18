package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.mail.feature.inbox.domain.ActionBarLayout
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.BarButton
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailAction.SuggestedMove
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.OwnAddresses
import com.otus.mail.feature.inbox.domain.barLayout
import com.otus.mail.feature.inbox.domain.replyAction
import com.otus.mail.feature.inbox.presentation.MessageActions

/**
 * The Read view's bar and what it offers. Filing acts on the [row], the whole conversation, while
 * replies follow the [page] on screen: the big button replies to all of its people or to its
 * sender alone, as that message's recipients call for. [offer] gives every action the bar and
 * menus can show; [more] is what the bar offers that the ⋯ menu has no entry for.
 */
internal class ReadBar(
    val layout: ActionBarLayout?,
    val offer: (BarButton) -> OfferedAction?,
    val more: List<OfferedAction>,
    val every: List<OfferedAction>,
    val minimized: Boolean,
)

@Composable
internal fun rememberReadBar(
    row: MailMessage,
    page: MailMessage,
    own: OwnAddresses,
    actions: MessageActions?,
    moveChoice: MoveChoice,
    onCompose: (ComposeRequest) -> Unit,
): ReadBar {
    val role = actions?.cleanup?.role?.invoke(row)
    val behavior = actions?.behavior()
    val layout = behavior?.barLayout(ActionBarPlace.Message, role)?.following(page.replyAction(own))
    val suggestion = rememberSuggestedMove(listOf(row), actions, layout?.buttons.orEmpty().contains(SuggestedMove))
    val offer = { action: BarButton ->
        actions?.offer(listOf(row), action, moveChoice::choose, suggestion) ?: replyOffer(page, action, role, onCompose)
    }
    // A minimized bar hides its secondary buttons, so the menu offers them.
    val minimized = behavior?.minimizedBar == true
    val shown = if (minimized) listOfNotNull(layout?.primary) else layout?.buttons.orEmpty()
    val covered = MENU_ACTIONS + shown
    val more =
        behavior
            ?.barChoices(ActionBarPlace.Message)
            .orEmpty()
            .filter { it !in covered }
            .mapNotNull(offer)
    val every = behavior?.everyAction(ActionBarPlace.Message, offer).orEmpty()
    return ReadBar(layout, offer, more, every, minimized)
}

/** The [bar], with the big button saying who the reply to the [page] goes to. */
@Composable
internal fun ReadActionBar(
    bar: ReadBar,
    page: MailMessage,
    onAction: (OfferedAction) -> Unit,
) {
    val layout = bar.layout ?: return
    MailActionBar(
        layout,
        bar.offer,
        onAction,
        Minimized().takeIf { bar.minimized },
        bar.every,
        context = replyContext(page, layout.primary),
    )
}

/** This layout with its reply button replying as [reply] does; another reply button takes the old one's slot. */
private fun ActionBarLayout.following(reply: MailAction): ActionBarLayout {
    val old = primary
    if ((old != MailAction.Reply && old != MailAction.ReplyAll) || old == reply) return this
    return copy(primary = reply, secondary = secondary.map { if (it == reply) old else it })
}

/** What the ⋯ menu already offers, so the menu's last section leaves it out. */
private val MENU_ACTIONS: List<BarButton> =
    listOf(
        MailAction.Read,
        MailAction.Flag,
        MailAction.ReadLater,
        MailAction.Spam,
        MailAction.Trash,
        MailAction.Move,
        MailAction.Reply,
        MailAction.ReplyAll,
        MailAction.Forward,
        MailAction.AllActions,
    )
