package com.otus.mail.feature.inbox.presentation.components

import com.otus.mail.feature.inbox.domain.MailAction
import org.jetbrains.compose.resources.DrawableResource

/**
 * An action as offered for some messages: its [label] for their current state, its [symbol], and
 * whether it takes them out of the list ([leaves]) before [run] does it. An action that [asks]
 * opens a choice first, which finishes it.
 */
internal class OfferedAction(
    val label: String,
    val symbol: DrawableResource,
    val leaves: Boolean,
    val asks: Boolean = false,
    val run: () -> Unit,
)

/** [action] under its own name and symbol, for an action whose label never changes. */
internal fun plainOffer(
    action: MailAction,
    run: () -> Unit,
) = OfferedAction(action.label, action.symbol, leaves = false, run = run)
