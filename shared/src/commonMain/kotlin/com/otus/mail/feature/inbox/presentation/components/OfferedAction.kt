package com.otus.mail.feature.inbox.presentation.components

import com.otus.mail.feature.inbox.domain.BarButton
import org.jetbrains.compose.resources.DrawableResource

/**
 * An action as offered for some messages: its [label] for their current state, its [symbol], and
 * whether it takes them out of the list ([leaves]) before [run] does it. An action that [asks]
 * opens a choice first, which finishes it. One with a [confirm] question runs only once the user
 * confirms it. The main button shows [primaryLabel], such as a folder's name where [label] reads
 * "Move to" and the name.
 */
internal class OfferedAction(
    val label: String,
    val symbol: DrawableResource,
    val leaves: Boolean,
    val asks: Boolean = false,
    val confirm: ConfirmQuestion? = null,
    val primaryLabel: String = label,
    val run: () -> Unit,
)

/** What a confirm dialog asks before an action that cannot be undone, and its button's [label]. */
internal class ConfirmQuestion(
    val title: String,
    val message: String,
    val label: String,
)

/** [action] under its own name and symbol, for an action whose label never changes. */
internal fun plainOffer(
    action: BarButton,
    run: () -> Unit,
) = OfferedAction(action.label, action.symbol, leaves = false, run = run)
