package com.otusfoss.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.otusfoss.core.designsystem.shared.rows.components.RowDensity
import com.otusfoss.core.settings.presentation.components.PageRowSpec
import com.otusfoss.core.settings.presentation.components.SettingRowSpec
import com.otusfoss.core.settings.presentation.components.SettingSpecRow
import com.otusfoss.core.settings.presentation.components.ToggleRowSpec
import com.otusfoss.mail.core.settings.domain.PrivacyPreset
import com.otusfoss.mail.core.settings.domain.privacyPreset
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import com.otusfoss.mail.feature.mailsettings.presentation.components.privacyRows
import com.otusfoss.mail.feature.notifications.domain.NotificationPreviews
import com.otusfoss.mail.feature.notifications.presentation.components.DeliveryChoices
import com.otusfoss.mail.feature.notifications.presentation.screens.deliveryLabel
import com.otusfoss.mail.feature.onboarding.presentation.components.OnboardingSlots
import com.otusfoss.mail.feature.onboarding.presentation.screens.OnboardingScreen
import com.otusfoss.mail.feature.theme.presentation.components.ThemeHueSlider

/**
 * Whether the first run shows. A device that has not finished it starts it once its saved
 * accounts have loaded and there are none. One that already has an account was set up before the
 * first run existed, so it counts as finished.
 */
@Composable
internal fun rememberOnboarding(
    states: MailStates,
    behavior: MailBehaviorState,
): Boolean {
    var started by rememberSaveable { mutableStateOf(false) }
    val accounts = states.accounts
    LaunchedEffect(accounts.loading) {
        if (accounts.loading || started || behavior.behavior.onboarded) return@LaunchedEffect
        if (accounts.accounts.isEmpty()) started = true else behavior.finishOnboarding()
    }
    return started && !behavior.behavior.onboarded
}

/**
 * Asks the device for permission to show alerts, which the platform's entry point provides around
 * [App]; null where there is nothing to ask, such as before Android 13 or once it is granted.
 */
val LocalAllowNotifications: ProvidableCompositionLocal<(() -> Unit)?> = staticCompositionLocalOf { null }

internal fun MailBehaviorState.finishOnboarding() = update { it.copy(onboarded = true) }

/**
 * The first run, with the privacy rows, delivery choices, and hue picker of the features that own
 * them. [LocalAllowNotifications] asks the device for permission to show alerts, where it needs
 * asking. [onAddAccount] runs once the first run has ended because the user wants another account.
 */
@Composable
internal fun OnboardingPage(
    states: MailStates,
    settings: SharedSettings,
    onAddAccount: () -> Unit,
) {
    val notifications = states.notifications
    OnboardingScreen(
        states.accounts,
        MailAccountService,
        states.behavior,
        settings.theme,
        OnboardingSlots(
            // The rows alone, without headings or rows that open a page, so the card stays a short list.
            privacySettings = {
                privacyRows(states.behavior, states.devicePrivacy) {}
                    .flatMap { it.rows }
                    .filterNot { it is PageRowSpec }
                    .map { it.titleOnly() }
                    .forEach { key(it.title) { SettingSpecRow(it, density = RowDensity.Compact) } }
            },
            onPreset = states::applyAlertPreset,
            delivery =
                notifications?.let {
                    { DeliveryChoices(it, private = states.behavior.behavior.privacyPreset() == PrivacyPreset.Private) }
                },
            deliveryLabel = notifications?.deliveryLabel,
            allowNotifications = LocalAllowNotifications.current,
            themeControls = { onTry -> ThemeHueSlider(settings.theme.choice, onTry) },
        ),
        onAddAccount = {
            states.behavior.finishOnboarding()
            onAddAccount()
        },
        onFinish = states.behavior::finishOnboarding,
    )
}

/** Sets what new-mail alerts show to what [preset] asks for, where alerts can show mail at all. */
internal fun MailStates.applyAlertPreset(preset: PrivacyPreset) {
    val previews = if (preset.hidesAlertContent) NotificationPreviews.Hidden else NotificationPreviews.Device
    notifications?.choosePreviews { it.copy(all = previews) }
}

/** A switch without the line under its title, so every row of the card is one line tall. */
private fun SettingRowSpec.titleOnly(): SettingRowSpec =
    if (this is ToggleRowSpec) ToggleRowSpec(title, checked, onCheckedChange, icon, info = info) else this
