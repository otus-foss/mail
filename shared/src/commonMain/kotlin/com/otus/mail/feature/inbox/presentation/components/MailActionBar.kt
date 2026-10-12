package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.ActionBar
import com.otus.designsystem.components.BarAction
import com.otus.designsystem.components.BarContext
import com.otus.mail.feature.inbox.domain.ActionBarLayout
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailBehavior

/**
 * Any screen's bar for [layout]. [offer] gives each action as it applies on this screen; one it
 * cannot offer stays in its slot, disabled, so the buttons never move. [context] describes what
 * the primary applies to, such as a reply's recipient.
 */
@Composable
internal fun MailActionBar(
    layout: ActionBarLayout,
    offer: (MailAction) -> OfferedAction?,
    onAction: (OfferedAction) -> Unit,
    modifier: Modifier = Modifier,
    context: BarContext? = null,
) {
    fun barAction(action: MailAction): BarAction {
        val offered = offer(action)
        return BarAction(
            offered?.label ?: action.buttonLabel,
            offered?.symbol ?: action.symbol,
            enabled =
                offered != null,
        ) {
            offered?.let(onAction)
        }
    }
    ActionBar(barAction(layout.primary), modifier, secondary = layout.secondary.map(::barAction), context = context)
}

/** Actions [place] can offer that its bar leaves out, for a page's menu; [offer] drops those that do not apply. */
internal fun MailBehavior.overflow(
    place: ActionBarPlace,
    offer: (MailAction) -> OfferedAction?,
): List<OfferedAction> {
    val layout = barLayout(place)
    return barChoices(place)
        .filter { it != layout.primary && it !in layout.secondary }
        .mapNotNull(offer)
}
