package com.otusfoss.mail.core.messages.presentation.actions

import com.otusfoss.core.designsystem.shared.overlays.components.ConfirmQuestion
import com.otusfoss.mail.core.messages.domain.BarButton
import org.jetbrains.compose.resources.DrawableResource

/**
 * An action as offered for some messages: its [label] for their current state, its [symbol], and
 * its [effect] on them once [run] does it. One with a [confirm] question runs only once the user
 * confirms it. The main button shows [primaryLabel], such as a folder's name where [label] reads
 * "Move to" and the name. A toggle, such as zen mode, says whether it is [on], which the bar
 * shows; null means the action is no toggle.
 */
internal class OfferedAction(
    val label: String,
    val symbol: DrawableResource,
    val effect: OfferEffect,
    val confirm: ConfirmQuestion? = null,
    val primaryLabel: String = label,
    val on: Boolean? = null,
    val run: () -> Unit,
) {
    /** Whether the action takes the messages out of the list. */
    val leaves: Boolean
        get() = effect == OfferEffect.Leaves

    /** Whether the action opens a choice first, which finishes it. */
    val asks: Boolean
        get() = effect == OfferEffect.Asks
}

/** What an [OfferedAction] does to its messages: they stay, leave the list, or a choice opens first. */
internal enum class OfferEffect {
    Stays,
    Leaves,
    Asks,
    ;

    companion object {
        fun of(leaves: Boolean) = if (leaves) Leaves else Stays
    }
}

/** [action] under its own name and symbol, for an action whose label never changes. */
internal fun plainOffer(
    action: BarButton,
    run: () -> Unit,
) = OfferedAction(action.label, action.symbol, OfferEffect.Stays, run = run)
