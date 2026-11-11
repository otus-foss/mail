package com.otusfoss.mail.feature.onboarding.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.theme.colors.ThemeChoice
import com.otusfoss.mail.core.settings.domain.PrivacyPreset

/**
 * What other features show inside the first run, which `app` fills. [privacySettings] are the
 * rows a privacy preset fills in, and [onPreset] applies a preset's settings that Mail's own
 * settings do not hold. [delivery] picks the service that delivers new mail, with
 * [deliveryLabel] naming the chosen one; both are null where the platform has no pushes, which
 * skips that step. [allowNotifications] asks the device for permission to show alerts, or is null
 * where there is nothing to ask. [themeControls] sit above the themes and hand colors to try to
 * the phone preview.
 */
class OnboardingSlots(
    val privacySettings: @Composable () -> Unit,
    val onPreset: (PrivacyPreset) -> Unit,
    val delivery: (@Composable () -> Unit)?,
    val deliveryLabel: String?,
    val allowNotifications: (() -> Unit)?,
    val themeControls: @Composable (onTry: (ThemeChoice) -> Unit) -> Unit,
)
