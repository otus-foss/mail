package com.otus.mail.core.messages.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.designsystem.shared.actionbar.components.ActionBar
import com.otus.designsystem.shared.actionbar.components.BarAction
import com.otus.designsystem.shared.actionbar.components.BarContext
import com.otus.designsystem.shared.actionbar.components.BarMinimized
import com.otus.designsystem.shared.controls.elements.UiAction
import com.otus.designsystem.shared.overlays.components.rememberConfirmation
import com.otus.mail.core.messages.domain.BarButton
import com.otus.mail.core.messages.domain.MailAction
import com.otus.mail.core.messages.presentation.actions.MailBar
import com.otus.mail.core.messages.presentation.actions.OfferedAction
import com.otus.mail.core.messages.presentation.actions.buttonLabel
import com.otus.mail.core.messages.presentation.actions.symbol

/**
 * Any screen's [bar]. An action it cannot offer stays in its slot, disabled, so the buttons never
 * move. All actions opens every action in a menu, and stays disabled while there is none; a drag
 * up on the buttons opens it too. [context] describes what the primary applies to, such as a
 * reply's recipient. A minimized bar shows only the primary, as [minimized] says. An action that
 * asks to be confirmed reaches [onAction] only once the user confirms it.
 */
@Composable
internal fun MailActionBar(
    bar: MailBar,
    onAction: (OfferedAction) -> Unit,
    modifier: Modifier = Modifier,
    minimized: Minimized = Minimized(),
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
                UiAction(label, button.symbol, enabled = bar.every.isNotEmpty()) {},
                menu = menuOf(bar.every),
            )
        }
        val offered = bar.offer(button)
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
    val layout = bar.layout
    ActionBar(
        barAction(layout.primary, primary = true),
        modifier,
        secondary = if (bar.minimized) emptyList() else layout.secondary.map { barAction(it, primary = false) },
        context = context,
        minimized = minimized.takeIf { bar.minimized }?.let { BarMinimized(it.hidden, menuOf(it.more)) },
        allActions = menuOf(bar.every),
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
