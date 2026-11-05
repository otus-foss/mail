package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.designsystem.shared.settings.components.SettingRow
import com.otus.designsystem.shared.settings.elements.ValueButton
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.NOTIFICATION_ACTION_LIMIT
import com.otus.mail.feature.inbox.domain.NotificationActionChoices
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.settings.presentation.SettingsIndexScope

/**
 * The Notifications setting for the buttons new-mail alerts offer, with the ordered sheet that
 * picks up to [NOTIFICATION_ACTION_LIMIT] of them, as the action bar names them.
 */
@Composable
internal fun NotificationActionsSetting(state: MailBehaviorState) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    SettingRow(
        "Alert buttons",
        { choosing = true },
        description = "Up to $NOTIFICATION_ACTION_LIMIT, in this order",
    ) { ValueButton(shownCount(state.behavior.notificationActions.size)) }
    if (!choosing) return
    OrderedChoiceSheet(
        "Alert buttons",
        state.behavior.notificationActions,
        NotificationActionChoices.map { OrderedChoice(it, it.storageValue, it.buttonLabel(), it.symbol, "Actions") },
        MailAction::storageValue,
        onApply = { actions -> state.update { it.copy(notificationActions = actions) } },
        onDismiss = { choosing = false },
        limit = NOTIFICATION_ACTION_LIMIT,
    )
}

/** Mirrors [NotificationActionsSetting] for settings search. */
fun SettingsIndexScope.notificationActionsIndex(behavior: MailBehavior) {
    setting(
        "Alert buttons",
        shownCount(behavior.notificationActions.size),
        NotificationActionChoices.map { it.buttonLabel() },
        keywords = listOf("Notification actions", "Quick actions"),
    )
}
