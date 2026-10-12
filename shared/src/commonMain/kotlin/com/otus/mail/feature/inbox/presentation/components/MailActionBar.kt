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
 * the primary applies to, such as a reply's recipient. A [minimized] bar shows only the primary.
 */
@Composable
internal fun MailActionBar(
    layout: ActionBarLayout,
    offer: (MailAction) -> OfferedAction?,
    onAction: (OfferedAction) -> Unit,
    minimized: Boolean,
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
    ActionBar(
        barAction(layout.primary),
        modifier,
        secondary = if (minimized) emptyList() else layout.secondary.map(::barAction),
        context = context,
        minimized = minimized,
    )
}

/**
 * Actions [place] can offer that its bar leaves out, for a page's menu, which takes the secondary
 * actions too while the bar is minimized. [offer] drops those that do not apply.
 */
internal fun MailBehavior.overflow(
    place: ActionBarPlace,
    offer: (MailAction) -> OfferedAction?,
): List<OfferedAction> {
    val layout = barLayout(place)
    val shown = if (minimizedBar) listOf(layout.primary) else layout.buttons
    return barChoices(place)
        .filter { it !in shown }
        .mapNotNull(offer)
}
