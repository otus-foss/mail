package com.otusfoss.mail.core.settings.domain

import com.otusfoss.mail.core.messages.domain.ActionBarLayout
import com.otusfoss.mail.core.messages.domain.ActionBarMode
import com.otusfoss.mail.core.messages.domain.ActionBarPlace
import com.otusfoss.mail.core.messages.domain.BarButton
import com.otusfoss.mail.core.messages.domain.ListFilter
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.QuickFacet

/**
 * Where actions show up. Swipes are named by layout direction: [startToEnd] is a swipe right in
 * left-to-right languages, and a null swipe does nothing. A swipe can also be a Move to button for
 * one folder. [bars] holds each place's bar, and
 * [barMode] picks whether places use their own or the Everywhere bar. A [minimizedBar] shows only
 * its primary, as a round button. [notificationActions] are the buttons, in order, that new-mail
 * alerts offer where the account can run them; see `alertButtons`. Read later is offered only
 * while it is on, which the choices take as `readLater`.
 */
data class ActionBehavior(
    val startToEnd: BarButton? = MailAction.Read,
    val endToStart: BarButton? = MailAction.Trash,
    val bars: Map<ActionBarPlace, ActionBarLayout> = ActionBarPlace.entries.associateWith { it.defaultLayout },
    val barMode: ActionBarMode = ActionBarMode.Adaptive,
    val minimizedBar: Boolean = false,
    val notificationActions: List<MailAction> = DefaultNotificationActions,
) {
    /** The plain actions a swipe can be set to, besides nothing and a Move to button. */
    fun swipeChoices(readLater: Boolean): List<MailAction> =
        MailAction.entries.filter { it.swipeable && offers(it, readLater) }

    /** The plain actions a bar slot at [place] can hold. */
    fun barChoices(
        place: ActionBarPlace,
        readLater: Boolean,
    ): List<MailAction> = place.actions.filter { offers(it, readLater) }

    /** The filters a Filter button can turn on and off. */
    fun filterChoices(readLater: Boolean): List<ListFilter> =
        ListFilter.all.filter { offers(BarButton.Filter(it), readLater) }

    /** The layout [place] shows: its own in Adaptive mode, the Everywhere one in Fixed mode. */
    fun barLayout(place: ActionBarPlace): ActionBarLayout =
        savedLayout(if (barMode == ActionBarMode.Fixed) ActionBarPlace.Everywhere else place)

    /** The layout saved for [place], whichever mode is on; settings edit this one. */
    fun savedLayout(place: ActionBarPlace): ActionBarLayout = bars[place] ?: place.defaultLayout

    /**
     * [from]'s bar as [to] would get it: the buttons [to] can offer, in order. When [to] cannot
     * offer [from]'s main button, [to] keeps its own.
     */
    fun copiedBar(
        from: ActionBarPlace,
        to: ActionBarPlace,
        readLater: Boolean,
    ): ActionBarLayout {
        val source = savedLayout(from)
        val offered = { button: BarButton -> to.offers(button) && offers(button, readLater) }
        val primary = source.primary.takeIf(offered) ?: savedLayout(to).primary
        return ActionBarLayout(source.secondary.filter { offered(it) && it != primary }, primary)
    }

    private fun offers(
        button: BarButton,
        readLater: Boolean,
    ): Boolean = readLater || button !in READ_LATER_BUTTONS
}

/** The buttons that need Read later on. */
private val READ_LATER_BUTTONS =
    setOf(MailAction.ReadLater, BarButton.Filter(ListFilter.Quick(QuickFacet.ReadLater)))
