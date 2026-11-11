package com.otusfoss.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceOptions
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceRow
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceSheet
import com.otusfoss.core.settings.presentation.components.ChoiceRowSpec
import com.otusfoss.mail.core.settings.domain.PrivacyPreset
import com.otusfoss.mail.core.settings.domain.privacyPreset
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import com.otusfoss.mail.core.settings.presentation.summary
import com.otusfoss.mail.core.settings.presentation.title

/**
 * The row that fills in the privacy settings from a [PrivacyPreset]. It names the preset the
 * settings match now, or "Custom" once one of them differs. [onPreset] applies what the picked
 * preset sets outside these settings.
 */
internal fun presetRow(
    state: MailBehaviorState,
    onPreset: (PrivacyPreset) -> Unit,
): ChoiceRowSpec =
    ChoiceRowSpec(
        "Preset",
        state.behavior.privacyPreset()?.title ?: "Custom",
        { PresetSheet(state, onPreset, it) },
        PrivacyPreset.entries.map { it.title },
    )

@Composable
private fun PresetSheet(
    state: MailBehaviorState,
    onPreset: (PrivacyPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    ChoiceSheet(
        "Preset",
        // No option is selected while the settings match neither preset.
        ChoiceOptions<PrivacyPreset?>(PrivacyPreset.entries, selected = state.behavior.privacyPreset()),
        row = { preset -> ChoiceRow(preset?.title.orEmpty(), preset?.summary) },
        onSelect = { preset ->
            if (preset != null) {
                state.update(preset::applyTo)
                onPreset(preset)
            }
        },
        onDismiss = onDismiss,
    )
}
