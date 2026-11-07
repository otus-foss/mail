package com.otus.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.shared.selection.components.OrderedChoice
import com.otus.designsystem.shared.selection.components.OrderedChoiceSheet
import com.otus.mail.core.messages.domain.MailAction
import com.otus.mail.core.messages.presentation.actions.buttonLabel
import com.otus.mail.core.messages.presentation.actions.symbol
import com.otus.mail.core.settings.domain.NOTIFICATION_ACTION_LIMIT
import com.otus.mail.core.settings.domain.NotificationActionChoices
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.settings.presentation.components.ChoiceRowSpec

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
