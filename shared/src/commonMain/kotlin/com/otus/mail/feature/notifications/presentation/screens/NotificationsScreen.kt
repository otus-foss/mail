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
import com.otus.mail.feature.notifications.domain.PushDeliveryStatus
import com.otus.mail.feature.notifications.domain.PushDistributor
import com.otus.mail.feature.notifications.presentation.NotificationSettingsState

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
                description = current?.let { "Through ${it.name}" } ?: "Off, checks about every 15 minutes",
            ) {
                ValueButton(current?.let { if (it.isGoogle) "Google" else it.name } ?: "Off")
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
            when {
                current?.isGoogle == true -> {
                    "Google receives encrypted change notices, never your mail. " +
                        "Your mail server must support Google delivery."
                }

                current != null -> {
                    "The push app sees only encrypted notices that something changed, never your mail."
                }

                state.distributors.any { it.isGoogle } -> {
                    "Choose Google Play services for instant delivery without an extra app."
                }

                else -> {
                    "For instant delivery, enable Google Play services or install a UnifiedPush app such as ntfy."
                }
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
    get() =
        this?.let { ChoiceRow(it.name, if (it.isGoogle) "Built into Otus" else "Through a separate app") }
            ?: ChoiceRow("Off", "Checks about every 15 minutes")

private val PushDeliveryStatus?.label: String
    get() =
        when (this) {
            PushDeliveryStatus.Active -> {
                "Instant"
            }

            PushDeliveryStatus.ServerRefused -> {
                "The server can't push, so Mail checks periodically"
            }

            PushDeliveryStatus.VapidRequired -> {
                "The server doesn't support Google delivery. Mail checks periodically."
            }

            PushDeliveryStatus.InvalidKey -> {
                "The server's push signing key is invalid. Mail checks periodically."
            }

            PushDeliveryStatus.Unavailable -> {
                "Couldn't connect. Mail will retry and keep checking periodically."
            }

            PushDeliveryStatus.RegistrationFailed -> {
                "Push registration failed. Mail will retry and keep checking periodically."
            }

            PushDeliveryStatus.Connecting, null -> {
                "Connecting"
            }
        }
