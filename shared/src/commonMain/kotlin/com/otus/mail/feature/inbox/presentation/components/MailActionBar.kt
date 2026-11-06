package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.designsystem.shared.actionbar.components.ActionBar
import com.otus.designsystem.shared.actionbar.components.BarAction
import com.otus.designsystem.shared.actionbar.components.BarContext
import com.otus.designsystem.shared.actionbar.components.BarMinimized
import com.otus.designsystem.shared.controls.elements.UiAction
import com.otus.designsystem.shared.overlays.components.rememberConfirmation
import com.otus.mail.feature.inbox.domain.ActionBarLayout
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.BarButton
import com.otus.mail.feature.inbox.domain.FolderRole
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.barLayout

/**
 * Any screen's bar for [layout]. [offer] gives each action as it applies on this screen; one it
 * cannot offer stays in its slot, disabled, so the buttons never move. All actions opens
 * [everyAction] in a menu, and stays disabled while there is none; a drag up on the buttons opens
 * it too. [context] describes what the
 * primary applies to, such as a reply's recipient. A [minimized] bar shows only the primary;
 * without one the bar is full. An action that asks to be confirmed reaches [onAction] only once
 * the user confirms it.
 */
@Composable
internal fun MailActionBar(
    layout: ActionBarLayout,
    offer: (BarButton) -> OfferedAction?,
    onAction: (OfferedAction) -> Unit,
    minimized: Minimized?,
    everyAction: List<OfferedAction>,
    modifier: Modifier = Modifier,
    context: BarContext? = null,
) {
    val confirmation = rememberConfirmation()
    val menuOf = { actions: List<OfferedAction> ->
        actions.map { UiAction(it.label, it.symbol) { confirmation.run(it.confirm) { onAction(it) } } }
    }

    fun barAction(
        button: BarButton,
        primary: Boolean,
    ): BarAction {
        if (button == MailAction.AllActions) {
            val label = button.buttonLabel(primary)
            return BarAction(
                UiAction(label, button.symbol, enabled = everyAction.isNotEmpty()) {},
                menu = menuOf(everyAction),
            )
        }
        val offered = offer(button)
        return BarAction(
            UiAction(
                (if (primary) offered?.primaryLabel else offered?.label) ?: button.buttonLabel(primary),
                offered?.symbol ?: button.symbol,
                enabled = offered != null,
            ) {
                if (offered != null) confirmation.run(offered.confirm) { onAction(offered) }
            },
        )
    }
    ActionBar(
        barAction(layout.primary, primary = true),
        modifier,
        secondary = if (minimized != null) emptyList() else layout.secondary.map { barAction(it, primary = false) },
        context = context,
        minimized = minimized?.let { BarMinimized(it.hidden, menuOf(it.more)) },
        allActions = menuOf(everyAction),
    )
    confirmation.Dialog()
}

/**
 * How a minimized bar differs from the full one: [hidden] scales its button out, and [more] lists
 * actions in a menu behind a round button at its start.
 */
internal class Minimized(
    val hidden: Boolean = false,
    val more: List<OfferedAction> = emptyList(),
)

/**
 * Actions [place] can offer that its bar leaves out, for a page's menu, after the secondary
 * buttons while the bar is minimized. [offer] drops those that do not apply. [role] is the page's
 * folder, whose bar may differ.
 */
internal fun MailBehavior.overflow(
    place: ActionBarPlace,
    offer: (BarButton) -> OfferedAction?,
    role: FolderRole? = null,
): List<OfferedAction> {
    val layout = barLayout(place, role)
    val hidden = if (minimizedBar) layout.secondary else emptyList()
    return (hidden + barChoices(place).filter { it !in layout.buttons }).mapNotNull(offer)
}

/** Every action [place] offers, for the All actions menu; [offer] drops those that do not apply. */
internal fun MailBehavior.everyAction(
    place: ActionBarPlace,
    offer: (BarButton) -> OfferedAction?,
): List<OfferedAction> = barChoices(place).filter { it != MailAction.AllActions }.mapNotNull(offer)
