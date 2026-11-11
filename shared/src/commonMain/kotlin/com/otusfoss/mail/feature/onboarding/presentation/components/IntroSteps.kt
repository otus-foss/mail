package com.otusfoss.mail.feature.onboarding.presentation.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otusfoss.core.designsystem.shared.controls.elements.UiAction
import com.otusfoss.core.designsystem.shared.onboarding.components.OnboardingHeroBody
import com.otusfoss.core.designsystem.shared.settings.components.SettingRow
import com.otusfoss.core.designsystem.shared.settings.elements.ValueButton
import com.otusfoss.mail.feature.onboarding.presentation.OnboardingStep

/**
 * The first step: what Otus is in one line, the one button that starts the account setup, and a
 * second that says which servers it works with.
 */
@Composable
internal fun ColumnScope.WelcomeStep(onNext: () -> Unit) {
    var providers by rememberSaveable { mutableStateOf(false) }
    OnboardingHeroBody(
        "Otus Mail",
        UiAction("Connect an account", onClick = onNext),
        body = "Connects straight to your own JMAP server.",
        link = UiAction(PROVIDERS_LABEL) { providers = true },
    )
    if (providers) ProvidersSheet { providers = false }
}

/** What the user chose in one step, as the last step lists it. */
internal class StepChoice(
    val step: OnboardingStep,
    val title: String,
    val value: String,
)

/**
 * The last step: every choice as a row that [onOpen]s its step again, then the button that
 * finishes the first run. [onAddAccount] finishes it too, and goes on to where accounts are added.
 */
@Composable
internal fun ColumnScope.ReadyStep(
    choices: List<StepChoice>,
    onOpen: (OnboardingStep) -> Unit,
    onAddAccount: () -> Unit,
    onFinish: () -> Unit,
) {
    OnboardingHeroBody(
        "You're set",
        UiAction("Open inbox", onClick = onFinish),
        secondary = UiAction("Add another account", onClick = onAddAccount),
    ) {
        choices.forEach { choice ->
            SettingRow(choice.title, { onOpen(choice.step) }) { ValueButton(choice.value) }
        }
    }
}
