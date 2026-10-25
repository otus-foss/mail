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
import com.otus.designsystem.components.PickMark
import com.otus.designsystem.components.PickRow
import com.otus.designsystem.components.PickRowLayout
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.components.SettingsSection
import com.otus.designsystem.components.Subpage
import com.otus.designsystem.elements.ValueButton
import com.otus.mail.feature.notifications.domain.NotificationPreviews
import com.otus.mail.feature.notifications.domain.PushDeliveryStatus
import com.otus.mail.feature.notifications.domain.PushDistributor
import com.otus.mail.feature.notifications.presentation.NotificationSettingsState

/** Delivery choices, registration status, and, where alerts can show mail, what they show. */
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
                description =
                    current?.let { "Through ${it.name}" }
                        ?: if (state.periodicChecks) "Off, checks about every 15 minutes" else "New-mail alerts off",
            ) {
                ValueButton(current?.let { if (it.isGoogle) "Google" else it.name } ?: "Off")
            }
        }
        state.previews?.let { previews ->
            SettingsSection("Previews") {
                NotificationPreviews.entries.forEach { choice ->
                    PickRow(
                        choice.title,
                        choice.description,
                        PickMark(choice == previews, atStart = true),
                        { state.choosePreviews(choice) },
                        layout = PickRowLayout(subtitleLines = PREVIEW_LINES),
                    )
                }
            }
        }
        if (current != null && accounts.isNotEmpty()) {
            SettingsSection("Accounts") {
                accounts.forEach { account ->
                    SettingRow(
                        account.name,
                        onClick = null,
                        description = state.statuses[account.id].label(state.periodicChecks),
                    )
                }
            }
        }
        PageNote(state.deliveryNote())
    }
    if (choosing) {
        ChoiceSheet(
            "Instant delivery",
            ChoiceOptions(listOf(null) + state.distributors, selected = current, default = null),
            row = { it.choiceRow(state.periodicChecks) },
            onSelect = {
                state.choose(it)
                choosing = false
            },
            onDismiss = { choosing = false },
        )
    }
}

private fun PushDistributor?.choiceRow(periodicChecks: Boolean): ChoiceRow =
    this?.let { ChoiceRow(it.name, if (it.isGoogle || it.isApple) "Built into Otus" else "Through a separate app") }
        ?: ChoiceRow("Off", if (periodicChecks) "Checks about every 15 minutes" else "No new-mail alerts")

private fun PushDeliveryStatus?.label(periodicChecks: Boolean): String {
    val fallback = if (periodicChecks) " Mail checks periodically." else " Open Mail to retry."
    val retry = if (periodicChecks) " Mail will retry and keep checking periodically." else " Open Mail to retry."
    return when (this) {
        PushDeliveryStatus.Active -> {
            "Instant"
        }

        PushDeliveryStatus.ServerRefused -> {
            if (periodicChecks) {
                "The server can't push, so Mail checks periodically"
            } else {
                "The server can't push.$fallback"
            }
        }

        PushDeliveryStatus.VapidRequired -> {
            "The server doesn't support Google delivery.$fallback"
        }

        PushDeliveryStatus.InvalidKey -> {
            "The server's push signing key is invalid.$fallback"
        }

        PushDeliveryStatus.Unavailable -> {
            "Couldn't connect.$retry"
        }

        PushDeliveryStatus.RegistrationFailed -> {
            "Push registration failed.$retry"
        }

        PushDeliveryStatus.Connecting, null -> {
            "Connecting"
        }
    }
}

private fun NotificationSettingsState.deliveryNote(): String =
    when {
        !this.periodicChecks -> {
            if (this.distributors.isEmpty()) {
                "Apple push is not configured in this build."
            } else {
                "Apple delivers encrypted change notices. Alerts show no message content. " +
                    "Open Mail regularly to renew delivery."
            }
        }

        current?.isGoogle == true -> {
            "Google receives encrypted change notices, never your mail. " +
                "Your mail server must support Google delivery."
        }

        current != null -> {
            "The push app sees only encrypted notices that something changed, never your mail."
        }

        this.distributors.any { it.isGoogle } -> {
            "Choose Google Play services for instant delivery without an extra app."
        }

        else -> {
            "For instant delivery, enable Google Play services or install a UnifiedPush app such as ntfy."
        }
    }

private val NotificationPreviews.title: String
    get() =
        when (this) {
            NotificationPreviews.Always -> "Always"
            NotificationPreviews.Unlocked -> "When unlocked"
            NotificationPreviews.Never -> "Never"
        }

private val NotificationPreviews.description: String
    get() =
        when (this) {
            NotificationPreviews.Always -> {
                "Sender and subject show on the lock screen too, unless the device hides sensitive content."
            }

            NotificationPreviews.Unlocked -> {
                "Nothing shows on the lock screen. Sender and subject show once the device is unlocked."
            }

            NotificationPreviews.Never -> {
                "Alerts say only \"New mail\"."
            }
        }

/** Enough lines for the longest description on a narrow phone. */
private const val PREVIEW_LINES = 3
