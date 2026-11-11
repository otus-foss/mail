package com.otusfoss.mail.feature.onboarding.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otusfoss.core.accounts.domain.AccountService
import com.otusfoss.core.accounts.presentation.AccountsState
import com.otusfoss.core.designsystem.shared.onboarding.components.OnboardingFrame
import com.otusfoss.core.settings.presentation.ThemeSettingsState
import com.otusfoss.core.settings.presentation.themeTitle
import com.otusfoss.mail.core.settings.domain.privacyPreset
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import com.otusfoss.mail.core.settings.presentation.title
import com.otusfoss.mail.feature.onboarding.presentation.OnboardingStep
import com.otusfoss.mail.feature.onboarding.presentation.components.ConnectStep
import com.otusfoss.mail.feature.onboarding.presentation.components.LookStep
import com.otusfoss.mail.feature.onboarding.presentation.components.NotificationsStep
import com.otusfoss.mail.feature.onboarding.presentation.components.OnboardingSlots
import com.otusfoss.mail.feature.onboarding.presentation.components.PrivacyStep
import com.otusfoss.mail.feature.onboarding.presentation.components.ReadyStep
import com.otusfoss.mail.feature.onboarding.presentation.components.StepChoice
import com.otusfoss.mail.feature.onboarding.presentation.components.WelcomeStep

/**
 * The first run: a welcome, the account sign-in it cannot go on without, a privacy preset, how
 * new mail arrives, the theme, and a summary whose button runs [onFinish]. Each step after the
 * sign-in saves its choice as it is made, so leaving early loses nothing. [slots] hold the parts
 * other features draw. [onAddAccount] ends the first run and opens the page that adds accounts.
 */
@Composable
fun OnboardingScreen(
    accounts: AccountsState,
    service: AccountService,
    behavior: MailBehaviorState,
    theme: ThemeSettingsState,
    slots: OnboardingSlots,
    onAddAccount: () -> Unit,
    onFinish: () -> Unit,
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = OnboardingStep.entries[index]
    val go = { next: OnboardingStep -> index = next.ordinal }
    val delivery = slots.delivery
    val back =
        previousStep(step, hasDelivery = delivery != null)?.let { previous ->
            {
                // Otherwise the finished sign-in would send the user straight on again.
                if (previous == OnboardingStep.Connect) accounts.cancelConnect()
                go(previous)
            }
        }
    OnboardingFrame(step.progress, back) {
        when (step) {
            OnboardingStep.Welcome -> {
                WelcomeStep { go(OnboardingStep.Connect) }
            }

            OnboardingStep.Connect -> {
                ConnectStep(accounts, service) { go(OnboardingStep.Privacy) }
            }

            OnboardingStep.Privacy -> {
                PrivacyStep(behavior, slots.privacySettings, slots.onPreset) {
                    go(if (delivery == null) OnboardingStep.Look else OnboardingStep.Notifications)
                }
            }

            OnboardingStep.Notifications -> {
                NotificationsStep(delivery ?: {}, slots.allowNotifications) {
                    go(OnboardingStep.Look)
                }
            }

            OnboardingStep.Look -> {
                LookStep(theme, slots.themeControls) { go(OnboardingStep.Ready) }
            }

            OnboardingStep.Ready -> {
                ReadyStep(choices(accounts, behavior, theme, slots), onOpen = go, onAddAccount, onFinish)
            }
        }
    }
}

/**
 * The step the back chevron returns to from [step]; the first and last have none. Without
 * [hasDelivery] the run has no Notifications step to return to.
 */
private fun previousStep(
    step: OnboardingStep,
    hasDelivery: Boolean,
): OnboardingStep? =
    when (step) {
        OnboardingStep.Welcome, OnboardingStep.Ready -> null
        OnboardingStep.Connect -> OnboardingStep.Welcome
        OnboardingStep.Privacy -> OnboardingStep.Connect
        OnboardingStep.Notifications -> OnboardingStep.Privacy
        OnboardingStep.Look -> if (hasDelivery) OnboardingStep.Notifications else OnboardingStep.Privacy
    }

/** What the user chose in each step, for the last step's rows. */
private fun choices(
    accounts: AccountsState,
    behavior: MailBehaviorState,
    theme: ThemeSettingsState,
    slots: OnboardingSlots,
): List<StepChoice> =
    listOfNotNull(
        StepChoice(OnboardingStep.Connect, "Account", accounts.accounts.joinToString { it.name }),
        StepChoice(OnboardingStep.Privacy, "Privacy", behavior.behavior.privacyPreset()?.title ?: "Custom"),
        slots.deliveryLabel?.let { StepChoice(OnboardingStep.Notifications, "Notifications", it) },
        StepChoice(OnboardingStep.Look, "Look", theme.choice.themeTitle),
    )
