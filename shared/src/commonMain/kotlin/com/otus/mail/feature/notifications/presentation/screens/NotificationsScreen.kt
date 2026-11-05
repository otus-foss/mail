package com.otus.mail.feature.notifications.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.accounts.domain.ConnectedAccount
import com.otus.designsystem.shared.feedback.components.InfoText
import com.otus.designsystem.shared.pages.components.Subpage
import com.otus.designsystem.shared.selection.components.ChoiceOptions
import com.otus.designsystem.shared.selection.components.ChoiceRow
import com.otus.designsystem.shared.selection.components.ChoiceSheet
import com.otus.designsystem.shared.settings.components.SettingPageRow
import com.otus.designsystem.shared.settings.components.SettingRow
import com.otus.designsystem.shared.settings.components.SettingsSection
import com.otus.designsystem.shared.settings.elements.ValueButton
import com.otus.mail.feature.notifications.domain.NotificationPreviews
import com.otus.mail.feature.notifications.domain.PreviewChoices
import com.otus.mail.feature.notifications.domain.PushDeliveryStatus
import com.otus.mail.feature.notifications.domain.PushDistributor
import com.otus.mail.feature.notifications.presentation.ButtonsInfo
import com.otus.mail.feature.notifications.presentation.NotificationSettingsState
import com.otus.mail.feature.notifications.presentation.PreviewsInfo
import com.otus.mail.feature.notifications.presentation.description
import com.otus.mail.feature.notifications.presentation.overrideSummary
import com.otus.mail.feature.notifications.presentation.short
import com.otus.mail.feature.notifications.presentation.title

/**
 * Delivery choices, registration status, and, where alerts can show mail, what they show and the
 * [buttons] setting for what their buttons do.
 */
@Composable
fun NotificationsScreen(
    state: NotificationSettingsState,
    accounts: List<ConnectedAccount>,
    onBack: () -> Unit,
    buttons: (@Composable () -> Unit)? = null,
    onOpenOverrides: () -> Unit,
) {
    LaunchedEffect(state) { state.watch() }
    var choosing by rememberSaveable { mutableStateOf(false) }
    val current = state.current
    Subpage("Notifications", onBack) {
        SettingsSection("New mail", info = InfoText("Instant delivery", state.deliveryNote())) {
            SettingRow(
                "Instant delivery",
                { choosing = true },
                description =
                    current?.let { "Through ${it.name}" }
                        ?: if (state.periodicChecks) "Off, checks about every 15 minutes" else "New-mail alerts off",
            ) {
                ValueButton(state.deliveryLabel)
            }
        }
        state.previews?.let { PreviewsSection(state, it, accounts, onOpenOverrides) }
        // Only alerts that show the message have buttons, so they come with previews.
        if (state.previews != null && buttons != null) SettingsSection("Buttons", info = ButtonsInfo) { buttons() }
        if (current != null) NotInstantSection(state, accounts)
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

/**
 * What alerts show for all mail, chosen in a sheet as the page's other choices are, and the page
 * that sets it per account and address.
 */
@Composable
private fun PreviewsSection(
    state: NotificationSettingsState,
    previews: PreviewChoices,
    accounts: List<ConnectedAccount>,
    onOpenOverrides: () -> Unit,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    SettingsSection("Previews", info = PreviewsInfo) {
        SettingRow("All mail", { choosing = true }, description = previews.all.description) {
            ValueButton(previews.all.short)
        }
        if (accounts.isNotEmpty()) {
            SettingPageRow("Accounts and addresses", onOpenOverrides, description = previews.overrideSummary(accounts))
        }
    }
    if (choosing) {
        ChoiceSheet(
            "All mail",
            ChoiceOptions(NotificationPreviews.entries, selected = previews.all, default = PreviewChoices().all),
            row = { ChoiceRow(it.title, it.description) },
            onSelect = { choice -> state.choosePreviews { it.copy(all = choice) } },
            onDismiss = { choosing = false },
        )
    }
}

/**
 * The accounts whose instant delivery fails, and why; nothing while every account is instant or
 * still connecting. Mail still arrives through periodic checks then, so nothing else tells the
 * user why alerts come late.
 */
@Composable
private fun NotInstantSection(
    state: NotificationSettingsState,
    accounts: List<ConnectedAccount>,
) {
    val healthy = setOf(PushDeliveryStatus.Active, PushDeliveryStatus.Connecting)
    val troubled = accounts.filter { account -> state.statuses[account.id].let { it != null && it !in healthy } }
    if (troubled.isEmpty()) return
    SettingsSection("Not instant", info = notInstantInfo(state.periodicChecks)) {
        troubled.forEach { account ->
            SettingRow(
                account.name,
                onClick = null,
                description = state.statuses[account.id].label(state.periodicChecks),
            )
        }
    }
}

private fun notInstantInfo(periodicChecks: Boolean) =
    InfoText(
        "Not instant",
        if (periodicChecks) {
            "Mail still arrives for these accounts through checks about every 15 minutes, and Mail keeps " +
                "retrying instant delivery."
        } else {
            "These accounts get no new-mail alerts until instant delivery works. Opening Mail retries it."
        },
    )

private fun PushDistributor?.choiceRow(periodicChecks: Boolean): ChoiceRow =
    this?.let { ChoiceRow(it.name, if (it.isGoogle || it.isApple) "Built into Otus" else "Through a separate app") }
        ?: ChoiceRow("Off", if (periodicChecks) "Checks about every 15 minutes" else "No new-mail alerts")

private fun PushDeliveryStatus?.label(periodicChecks: Boolean): String {
    // With periodic checks mail still arrives, which the section's info explains; without them the
    // user has to act, so the row says so.
    val fallback = if (periodicChecks) "" else " Open Mail to retry."
    return when (this) {
        PushDeliveryStatus.Active -> {
            "Instant"
        }

        PushDeliveryStatus.ServerRefused -> {
            "The server can't push.$fallback"
        }

        PushDeliveryStatus.VapidRequired -> {
            "The server doesn't support Google delivery.$fallback"
        }

        PushDeliveryStatus.InvalidKey -> {
            "The server's push signing key is invalid.$fallback"
        }

        PushDeliveryStatus.Unavailable -> {
            "Couldn't connect.$fallback"
        }

        PushDeliveryStatus.RegistrationFailed -> {
            "Push registration failed.$fallback"
        }

        PushDeliveryStatus.Connecting, null -> {
            "Connecting"
        }
    }
}

private fun NotificationSettingsState.deliveryNote(): String =
    when {
        !periodicChecks -> {
            if (distributors.isEmpty()) {
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

        distributors.any { it.isGoogle } -> {
            "Choose Google Play services for instant delivery without an extra app."
        }

        else -> {
            "For instant delivery, enable Google Play services or install a UnifiedPush app such as ntfy."
        }
    }

/** The Instant delivery row's value: the distributor that delivers new mail, or Off. */
internal val NotificationSettingsState.deliveryLabel: String
    get() = current?.let { if (it.isGoogle) "Google" else it.name } ?: "Off"
