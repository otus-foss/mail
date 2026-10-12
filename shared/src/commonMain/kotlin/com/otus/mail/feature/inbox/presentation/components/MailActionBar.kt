package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.ActionBar
import com.otus.designsystem.components.BarAction
import com.otus.designsystem.components.BarContext
import com.otus.mail.feature.inbox.domain.ActionBarLayout
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.BarButton
import com.otus.mail.feature.inbox.domain.MailBehavior

/**
 * Any screen's bar for [layout]. [offer] gives each action as it applies on this screen; one it
 * cannot offer stays in its slot, disabled, so the buttons never move. [context] describes what
 * the primary applies to, such as a reply's recipient. A [minimized] bar shows only the primary,
 * and scales it out while [hidden].
 */
@Composable
internal fun MailActionBar(
    layout: ActionBarLayout,
    offer: (BarButton) -> OfferedAction?,
    onAction: (OfferedAction) -> Unit,
    minimized: Boolean,
    modifier: Modifier = Modifier,
    context: BarContext? = null,
    hidden: Boolean = false,
) {
    fun barAction(
        button: BarButton,
        primary: Boolean,
    ): BarAction {
        val offered = offer(button)
        return BarAction(
            (if (primary) offered?.primaryLabel else offered?.label) ?: button.buttonLabel(primary),
            offered?.symbol ?: button.symbol,
            enabled = offered != null,
        ) {
            offered?.let(onAction)
        }
    }
    ActionBar(
        barAction(layout.primary, primary = true),
        modifier,
        secondary = if (minimized) emptyList() else layout.secondary.map { barAction(it, primary = false) },
        context = context,
        minimized = minimized,
        hidden = hidden,
    )
}

/**
 * Actions [place] can offer that its bar leaves out, for a page's menu, after the secondary
 * buttons while the bar is minimized. [offer] drops those that do not apply.
 */
internal fun MailBehavior.overflow(
    place: ActionBarPlace,
    offer: (BarButton) -> OfferedAction?,
): List<OfferedAction> {
    val layout = barLayout(place)
    val hidden = if (minimizedBar) layout.secondary else emptyList()
    return (hidden + barChoices(place).filter { it !in layout.buttons }).mapNotNull(offer)
}
