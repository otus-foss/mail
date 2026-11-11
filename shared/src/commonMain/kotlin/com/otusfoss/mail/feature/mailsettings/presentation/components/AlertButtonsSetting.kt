package com.otusfoss.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.shared.selection.components.OrderedChoice
import com.otusfoss.core.designsystem.shared.selection.components.OrderedChoiceSheet
import com.otusfoss.core.settings.presentation.components.ChoiceRowSpec
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.presentation.actions.buttonLabel
import com.otusfoss.mail.core.messages.presentation.actions.symbol
import com.otusfoss.mail.core.settings.domain.NOTIFICATION_ACTION_LIMIT
import com.otusfoss.mail.core.settings.domain.NotificationActionChoices
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState

/**
 * The Notifications setting for the buttons new-mail alerts offer, with the ordered sheet that
 * picks up to [NOTIFICATION_ACTION_LIMIT] of them, as the action bar names them.
 */
fun alertButtonsRow(state: MailBehaviorState): ChoiceRowSpec =
    ChoiceRowSpec(
        "Alert buttons",
        shownCount(state.behavior.actions.notificationActions.size),
        { AlertButtonsSheet(state, it) },
        NotificationActionChoices.map { it.buttonLabel() },
        description = "Up to $NOTIFICATION_ACTION_LIMIT, in this order",
        keywords = listOf("Notification actions", "Quick actions"),
    )

@Composable
private fun AlertButtonsSheet(
    state: MailBehaviorState,
    onDismiss: () -> Unit,
) {
    OrderedChoiceSheet(
        "Alert buttons",
        state.behavior.actions.notificationActions,
        NotificationActionChoices.map { OrderedChoice(it, it.storageValue, it.buttonLabel(), it.symbol, "Actions") },
        MailAction::storageValue,
        onApply = { actions -> state.updateActions { it.copy(notificationActions = actions) } },
        onDismiss = onDismiss,
        limit = NOTIFICATION_ACTION_LIMIT,
    )
}
