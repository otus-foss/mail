package com.otusfoss.mail.core.messages.presentation.actions

import com.otusfoss.core.designsystem.shared.controls.elements.UiAction
import com.otusfoss.core.designsystem.shared.swipe.components.SwipeAction
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.ActionBarLayout
import com.otusfoss.mail.core.messages.domain.ActionBarPlace
import com.otusfoss.mail.core.messages.domain.BarButton
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.barLayout
import com.otusfoss.mail.core.settings.domain.ActionBehavior

/**
 * One place's bar: its [layout], each button as [offer] gives it, whether it is [minimized] to its
 * primary, and [every] action the place offers, for the All actions menu.
 */
internal class MailBar(
    val layout: ActionBarLayout,
    val offer: (BarButton) -> OfferedAction?,
    val minimized: Boolean,
    val every: List<OfferedAction>,
    private val offBar: List<BarButton>,
) {
    /**
     * What the place offers that the bar does not show, for a menu: the secondary buttons while it
     * is minimized, then the actions it has no slot for, without those the menu shows elsewhere.
     */
    fun more(shownElsewhere: Set<BarButton> = emptySet()): List<OfferedAction> =
        offBar.filter { it !in shownElsewhere }.mapNotNull(offer)
}

/**
 * The bar [place] shows on a page of [role] folders, with each button running on what [offer]
 * gives. A [reply] primary, such as Reply all for a message to several people, takes the place
 * of a saved Reply or Reply all primary.
 */
internal fun ActionBehavior.barFor(
    place: ActionBarPlace,
    role: MailboxRole?,
    offer: (BarButton) -> OfferedAction?,
    reply: MailAction? = null,
): MailBar {
    val layout = barLayout(place, role).let { if (reply == null) it else it.following(reply) }
    val choices = place.actions.filter { it != MailAction.AllActions }
    val hidden = if (minimizedBar) layout.secondary else emptyList()
    return MailBar(
        layout,
        offer,
        minimizedBar,
        every = choices.mapNotNull(offer),
        offBar = hidden + choices.filter { it !in layout.buttons },
    )
}

/** This layout with its reply button replying as [reply] does; another reply button takes the old one's slot. */
private fun ActionBarLayout.following(reply: MailAction): ActionBarLayout {
    val old = primary
    if ((old != MailAction.Reply && old != MailAction.ReplyAll) || old == reply) return this
    return copy(primary = reply, secondary = secondary.map { if (it == reply) old else it })
}

/** The swipe that runs [action] on what [subject] holds; null when there is none or it cannot take it. */
internal fun ActionSubject.swipe(action: MailAction?): SwipeAction? =
    action
        ?.takeIf { it.swipeable }
        ?.let(::offer)
        ?.let { SwipeAction(UiAction(it.label, it.symbol, onClick = it.run), dismisses = it.leaves) }

/** Whether [place]'s bar on a page of [role] folders has Suggested move, which asks the server for its folder. */
internal fun ActionBehavior.showsSuggestedMove(
    place: ActionBarPlace,
    role: MailboxRole?,
): Boolean = MailAction.SuggestedMove in barLayout(place, role).buttons
