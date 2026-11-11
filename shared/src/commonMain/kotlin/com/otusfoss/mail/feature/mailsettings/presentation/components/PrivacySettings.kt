package com.otusfoss.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_edit
import com.otusfoss.core.designsystem.resources.symbol_image
import com.otusfoss.core.designsystem.resources.symbol_photo_camera
import com.otusfoss.core.designsystem.resources.symbol_verified_user
import com.otusfoss.core.designsystem.resources.symbol_visibility_off
import com.otusfoss.core.designsystem.shared.feedback.components.InfoText
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceOptions
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceRow
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceSheet
import com.otusfoss.core.settings.presentation.components.ChoiceRowSpec
import com.otusfoss.core.settings.presentation.components.PageRowSpec
import com.otusfoss.core.settings.presentation.components.SettingRowSpec
import com.otusfoss.core.settings.presentation.components.SettingsSectionSpec
import com.otusfoss.core.settings.presentation.components.ToggleRowSpec
import com.otusfoss.mail.core.messages.domain.DevicePrivacy
import com.otusfoss.mail.core.messages.domain.RemoteImages
import com.otusfoss.mail.core.settings.domain.PhotoCleaning
import com.otusfoss.mail.core.settings.domain.PrivacyPreset
import com.otusfoss.mail.core.settings.domain.ReadBehavior
import com.otusfoss.mail.core.settings.domain.WriteBehavior
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import com.otusfoss.mail.core.settings.presentation.allowedSummary
import com.otusfoss.mail.core.settings.presentation.description
import com.otusfoss.mail.core.settings.presentation.title

/**
 * Settings › Privacy & security: when mail loads images from other servers, chosen in a sheet, who
 * may always show them ([openAllowed] opens that list), what photos you attach keep, the avatars,
 * and the screen and keyboard protections that [device] offers. The Preset row on top fills them
 * in from a [PrivacyPreset]; [onPreset] applies what that preset sets outside these settings. Each
 * switch and each choice saves at once. Website icons are off by default, so avatars reach only
 * the user's servers.
 */
fun privacySettings(
    state: MailBehaviorState,
    device: DevicePrivacy,
    onPreset: (PrivacyPreset) -> Unit = {},
    openAllowed: (setting: String?) -> Unit,
): List<SettingsSectionSpec> =
    listOf(SettingsSectionSpec(null, listOf(presetRow(state, onPreset)))) + privacyRows(state, device, openAllowed)

/**
 * The privacy settings a preset fills in, by section, without the Preset row itself: what the
 * first run shows under a preset, and the rest of the Privacy & security page.
 */
fun privacyRows(
    state: MailBehaviorState,
    device: DevicePrivacy,
    openAllowed: (setting: String?) -> Unit,
): List<SettingsSectionSpec> {
    val behavior = state.behavior
    val images =
        listOf(
            ChoiceRowSpec(
                "Load images",
                behavior.read.remoteImages.title,
                { ImagesSheet(state, it) },
                RemoteImages.entries.map { it.title },
                Res.drawable.symbol_image,
                keywords = listOf("Remote content", "Tracking pixels"),
            ),
            PageRowSpec(
                "Allowed senders",
                openAllowed,
                value = allowedSummary(behavior.read.allowedSenders.size, behavior.read.remoteImages),
                icon = Res.drawable.symbol_verified_user,
            ),
        )
    val photos =
        ChoiceRowSpec(
            "Photo details",
            behavior.write.photoCleaning.title,
            { PhotosSheet(state, it) },
            PhotoCleaning.entries.map { it.title },
            Res.drawable.symbol_photo_camera,
            keywords = listOf("Location", "Metadata", "EXIF"),
        )
    return listOf(
        SettingsSectionSpec("Load remote images", images, ImagesInfo),
        SettingsSectionSpec("Photos you attach", listOf(photos), PhotosInfo),
        avatarSettings(state),
        SettingsSectionSpec("This device", deviceRows(state, device)),
    )
}

/** The screen and keyboard protections [device] offers. */
private fun deviceRows(
    state: MailBehaviorState,
    device: DevicePrivacy,
): List<SettingRowSpec> {
    val privacy = state.behavior.privacy
    return listOfNotNull(
        ToggleRowSpec(
            "Screen security",
            privacy.screenSecurity,
            { on -> state.updatePrivacy { it.copy(screenSecurity = on) } },
            Res.drawable.symbol_visibility_off,
            description = "Hides Mail from recent apps and screenshots",
            keywords = listOf("Screenshots", "Recent apps"),
        ).takeIf { device.screen },
        ToggleRowSpec(
            "Incognito keyboard",
            privacy.incognitoKeyboard,
            { on -> state.updatePrivacy { it.copy(incognitoKeyboard = on) } },
            Res.drawable.symbol_edit,
            description = "Asks the keyboard not to learn what you type",
            info = InfoText("Incognito keyboard", "Some keyboards ignore the request and learn anyway."),
            keywords = listOf("Typing", "Learning"),
        ).takeIf { device.keyboard },
    )
}

@Composable
private fun ImagesSheet(
    state: MailBehaviorState,
    onDismiss: () -> Unit,
) {
    ChoiceSheet(
        "Load images",
        ChoiceOptions(RemoteImages.entries, state.behavior.read.remoteImages, ReadBehavior().remoteImages),
        row = { ChoiceRow(it.title, it.description) },
        onSelect = { choice -> state.updateRead { it.copy(remoteImages = choice) } },
        onDismiss = onDismiss,
    )
}

@Composable
private fun PhotosSheet(
    state: MailBehaviorState,
    onDismiss: () -> Unit,
) {
    ChoiceSheet(
        "Photo details",
        ChoiceOptions(PhotoCleaning.entries, state.behavior.write.photoCleaning, WriteBehavior().photoCleaning),
        row = { ChoiceRow(it.title, it.description) },
        onSelect = { choice -> state.updateWrite { it.copy(photoCleaning = choice) } },
        onDismiss = onDismiss,
    )
}

private val ImagesInfo =
    InfoText(
        "Remote images",
        "Images that are part of the mail itself always show. Mail in Spam never loads remote images.",
    )

private val PhotosInfo =
    InfoText(
        "Photo details",
        "Photos can carry where, when, and with what camera they were taken, and their file name.",
    )
