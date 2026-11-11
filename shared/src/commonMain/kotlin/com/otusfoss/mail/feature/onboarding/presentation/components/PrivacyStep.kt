package com.otusfoss.mail.feature.onboarding.presentation.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_image
import com.otusfoss.core.designsystem.resources.symbol_notifications
import com.otusfoss.core.designsystem.resources.symbol_notifications_off
import com.otusfoss.core.designsystem.resources.symbol_photo_camera
import com.otusfoss.core.designsystem.resources.symbol_public
import com.otusfoss.core.designsystem.resources.symbol_shield
import com.otusfoss.core.designsystem.resources.symbol_visibility_off
import com.otusfoss.core.designsystem.shared.controls.elements.ActionButton
import com.otusfoss.core.designsystem.shared.onboarding.components.OnboardingStepBody
import com.otusfoss.core.designsystem.shared.onboarding.components.PresetCard
import com.otusfoss.core.designsystem.shared.onboarding.components.PresetChoice
import com.otusfoss.core.designsystem.shared.onboarding.components.PresetMark
import com.otusfoss.mail.core.messages.domain.RemoteImages
import com.otusfoss.mail.core.settings.domain.MailBehavior
import com.otusfoss.mail.core.settings.domain.PhotoCleaning
import com.otusfoss.mail.core.settings.domain.PrivacyPreset
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import com.otusfoss.mail.core.settings.presentation.title

/**
 * What [this] preset decides, read from the settings it fills in, so the marks cannot drift from
 * the preset: remote images, where sender icons come from, what alerts show, and the details
 * photos keep. Each names its value in a word.
 */
private fun PrivacyPreset.marks(): List<PresetMark> {
    val filled = applyTo(MailBehavior())
    val images = filled.read.remoteImages != RemoteImages.Never
    val icons = filled.avatars.websiteIcons
    val details = filled.write.photoCleaning != PhotoCleaning.Always
    return listOf(
        PresetMark(Res.drawable.symbol_image, images, "Images", if (images) "Contacts" else "Never"),
        PresetMark(Res.drawable.symbol_public, icons, "Icons", if (icons) filled.avatars.iconSource.name else "Off"),
        PresetMark(
            if (hidesAlertContent) Res.drawable.symbol_notifications_off else Res.drawable.symbol_notifications,
            on = true,
            "Alerts",
            if (hidesAlertContent) "Hidden" else "Shown",
        ),
        PresetMark(Res.drawable.symbol_photo_camera, details, "Photos", if (details) "Ask" else "Cleaned"),
        PresetMark(
            if (filled.privacy.screenSecurity) Res.drawable.symbol_shield else Res.drawable.symbol_visibility_off,
            on = true,
            "Screen",
            if (filled.privacy.screenSecurity) "Hidden" else "Shown",
        ),
    )
}

/**
 * Picks the privacy preset to start from. Picking one fills in its settings at once, through
 * [state] and [onPreset]; "See settings" picks the card too and shows [settings], the rows it
 * filled in, which change the saved settings directly. A card whose settings were changed since
 * says "Edited". The button names the pick, and waits for one.
 */
@Composable
internal fun ColumnScope.PrivacyStep(
    state: MailBehaviorState,
    settings: @Composable () -> Unit,
    onPreset: (PrivacyPreset) -> Unit,
    onNext: () -> Unit,
) {
    // Saved by name, so the pick survives a recreated activity.
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val pick = { preset: PrivacyPreset ->
        picked = preset.name
        state.update(preset::applyTo)
        onPreset(preset)
    }
    val label = PrivacyPreset.entries.firstOrNull { it.name == picked }?.let { "Use ${it.title}" }
    OnboardingStepBody(
        "Choose your privacy",
        footer = {
            ActionButton(label ?: "Continue", onNext, Modifier.fillMaxWidth(), primary = true, enabled = label != null)
        },
    ) {
        PrivacyPreset.entries.forEach { preset ->
            val selected = picked == preset.name
            PresetCard(
                PresetChoice(
                    preset.title,
                    preset.marks(),
                    note = "Edited".takeIf { selected && preset.applyTo(state.behavior) != state.behavior },
                ),
                selected,
                expanded = expanded == preset.name,
                onSelect = { pick(preset) },
                onExpandedChange = { open ->
                    if (open && !selected) pick(preset)
                    expanded = preset.name.takeIf { open }
                },
            ) { settings() }
        }
    }
}
