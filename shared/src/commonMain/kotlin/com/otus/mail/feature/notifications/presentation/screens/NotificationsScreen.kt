package com.otus.mail.feature.notifications.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.accounts.domain.ConnectedAccount
import com.otus.designsystem.components.ChoiceOptions
import com.otus.designsystem.components.ChoiceRow
import com.otus.designsystem.components.ChoiceSheet
import com.otus.designsystem.components.PageNote
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.components.SettingsSection
import com.otus.designsystem.components.Subpage
import com.otus.designsystem.elements.ValueButton
import com.otus.mail.feature.notifications.domain.PushDistributor
import com.otus.mail.feature.notifications.presentation.NotificationSettingsState
import com.otus.push.PushStatus

/**
 * Chooses how new mail arrives: through a UnifiedPush app within seconds, or by a check about every
 * 15 minutes. With a push app chosen, each account shows whether its server pushes.
 */
@Composable
fun NotificationsScreen(
    state: NotificationSettingsState,
    accounts: List<ConnectedAccount>,
    onBack: () -> Unit,
) {
    LaunchedEffect(state) { state.watch() }
    var choosing by rememberSaveable { mutableStateOf(false) }
    val current = state.current
    Subpage("Notifications", onBack) {
        SettingsSection("New mail") {
            SettingRow(
                "Instant delivery",
                { choosing = true },
                description = current?.let { "Through ${it.name}, within seconds" } ?: "Off, checks every 15 minutes",
            ) {
                ValueButton(current?.name ?: "Off")
            }
        }
        if (current != null && accounts.isNotEmpty()) {
            SettingsSection("Accounts") {
                accounts.forEach { account ->
                    SettingRow(account.name, onClick = null, description = state.statuses[account.id].label)
                }
            }
        }
        PageNote(
            if (state.distributors.isEmpty()) {
                "To get new mail within seconds, install a UnifiedPush app such as ntfy, then choose it here."
            } else {
                "The push app sees only encrypted notices that something changed, never your mail."
            },
        )
    }
    if (choosing) {
        ChoiceSheet(
            "Instant delivery",
            ChoiceOptions(listOf(null) + state.distributors, selected = current, default = null),
            row = { it.choiceRow },
            onSelect = {
                state.choose(it)
                choosing = false
            },
            onDismiss = { choosing = false },
        )
    }
}

private val PushDistributor?.choiceRow: ChoiceRow
    get() = this?.let { ChoiceRow(it.name, "Within seconds") } ?: ChoiceRow("Off", "Checks every 15 minutes")

private val PushStatus?.label: String
    get() =
        when (this) {
            PushStatus.Active -> "Instant"
            PushStatus.Refused -> "The server can't push, so Mail checks every 15 minutes"
            PushStatus.Waiting, PushStatus.Off, null -> "Connecting"
        }
