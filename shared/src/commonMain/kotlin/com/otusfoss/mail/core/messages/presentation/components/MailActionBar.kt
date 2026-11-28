package com.otusfoss.mail.core.messages.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otusfoss.core.designsystem.shared.actionbar.components.ActionBar
import com.otusfoss.core.designsystem.shared.actionbar.components.BarAction
import com.otusfoss.core.designsystem.shared.actionbar.components.BarContext
import com.otusfoss.core.designsystem.shared.controls.elements.UiAction
import com.otusfoss.core.designsystem.shared.overlays.components.rememberConfirmation
import com.otusfoss.mail.core.messages.domain.BarButton
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.presentation.actions.MailBar
import com.otusfoss.mail.core.messages.presentation.actions.OfferedAction
import com.otusfoss.mail.core.messages.presentation.actions.buttonLabel
import com.otusfoss.mail.core.messages.presentation.actions.symbol

/**
 * Any screen's [bar]. An action it cannot offer stays in its slot, disabled, so the buttons never
 * move. All actions opens every action in a menu, and stays disabled while there is none; a drag
 * up on the buttons opens it too. [context] describes what the primary applies to, such as a
 * reply's recipient. An action that asks to be confirmed reaches [onAction] only once the user
 * confirms it.
 */
@Composable
internal fun MailActionBar(
    bar: MailBar,
    onAction: (OfferedAction) -> Unit,
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
        secondary = layout.secondary.map { barAction(it, primary = false) },
        context = context,
        allActions = menuOf(bar.every),
    )
    confirmation.Dialog()
}
