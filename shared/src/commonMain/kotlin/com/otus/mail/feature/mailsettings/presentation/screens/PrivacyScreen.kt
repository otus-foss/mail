package com.otus.mail.feature.mailsettings.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_edit
import com.otus.designsystem.resources.symbol_image
import com.otus.designsystem.resources.symbol_photo_camera
import com.otus.designsystem.resources.symbol_verified_user
import com.otus.designsystem.resources.symbol_visibility_off
import com.otus.designsystem.shared.feedback.components.InfoText
import com.otus.designsystem.shared.pages.components.Subpage
import com.otus.designsystem.shared.selection.components.ChoiceOptions
import com.otus.designsystem.shared.selection.components.ChoiceRow
import com.otus.designsystem.shared.selection.components.ChoiceSheet
import com.otus.designsystem.shared.settings.components.SettingPageRow
import com.otus.designsystem.shared.settings.components.SettingRow
import com.otus.designsystem.shared.settings.components.SettingToggleRow
import com.otus.designsystem.shared.settings.components.SettingsSection
import com.otus.designsystem.shared.settings.elements.ValueButton
import com.otus.mail.core.messages.domain.DevicePrivacy
import com.otus.mail.core.messages.domain.RemoteImages
import com.otus.mail.core.settings.domain.PhotoCleaning
import com.otus.mail.core.settings.domain.ReadBehavior
import com.otus.mail.core.settings.domain.WriteBehavior
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.core.settings.presentation.PRIVACY_TITLE
import com.otus.mail.core.settings.presentation.allowedSummary
import com.otus.mail.core.settings.presentation.description
import com.otus.mail.core.settings.presentation.title
import com.otus.mail.feature.mailsettings.presentation.components.AvatarSettings

/**
 * Settings › Privacy & security: when mail loads images from other servers, chosen in a sheet, who
 * may always show them ([onOpenAllowed] opens that list), whether avatars load website icons, and
 * the screen and keyboard protections that [device] offers. Each switch and each choice saves at
 * once. Website icons are off by default, so avatars reach only the user's servers.
 */
@Composable
fun PrivacyScreen(
    state: MailBehaviorState,
    onOpenAllowed: () -> Unit,
    onBack: () -> Unit,
    device: DevicePrivacy = DevicePrivacy(),
) {
    val behavior = state.behavior
    var choosingImages by rememberSaveable { mutableStateOf(false) }
    var choosingPhotos by rememberSaveable { mutableStateOf(false) }
    Subpage(PRIVACY_TITLE, onBack) {
        SettingsSection("Load remote images", info = ImagesInfo) {
            SettingRow("Load images", { choosingImages = true }, icon = Res.drawable.symbol_image) {
                ValueButton(behavior.read.remoteImages.title)
            }
            val count = behavior.read.allowedSenders.size
            SettingPageRow(
                "Allowed senders",
                onOpenAllowed,
                description = allowedSummary(count, behavior.read.remoteImages),
                icon = Res.drawable.symbol_verified_user,
            )
        }
        SettingsSection("Photos you attach", info = PhotosInfo) {
            SettingRow("Photo details", { choosingPhotos = true }, icon = Res.drawable.symbol_photo_camera) {
                ValueButton(behavior.write.photoCleaning.title)
            }
        }
        AvatarSettings(state)
        if (device.screen || device.keyboard) DeviceSection(state, device)
    }
    if (choosingImages) {
        ChoiceSheet(
            "Load images",
            ChoiceOptions(RemoteImages.entries, behavior.read.remoteImages, ReadBehavior().remoteImages),
            row = { ChoiceRow(it.title, it.description) },
            onSelect = { choice -> state.updateRead { it.copy(remoteImages = choice) } },
            onDismiss = { choosingImages = false },
        )
    }
    if (choosingPhotos) {
        ChoiceSheet(
            "Photo details",
            ChoiceOptions(PhotoCleaning.entries, behavior.write.photoCleaning, WriteBehavior().photoCleaning),
            row = { ChoiceRow(it.title, it.description) },
            onSelect = { choice -> state.updateWrite { it.copy(photoCleaning = choice) } },
            onDismiss = { choosingPhotos = false },
        )
    }
}

/** The screen and keyboard protections [device] offers. */
@Composable
private fun DeviceSection(
    state: MailBehaviorState,
    device: DevicePrivacy,
) {
    val privacy = state.behavior.privacy
    SettingsSection("This device") {
        if (device.screen) {
            SettingToggleRow(
                "Screen security",
                checked = privacy.screenSecurity,
                onCheckedChange = { on -> state.updatePrivacy { it.copy(screenSecurity = on) } },
                description = "Hides Mail from recent apps and screenshots",
                icon = Res.drawable.symbol_visibility_off,
            )
        }
        if (device.keyboard) {
            SettingToggleRow(
                "Incognito keyboard",
                checked = privacy.incognitoKeyboard,
                onCheckedChange = { on -> state.updatePrivacy { it.copy(incognitoKeyboard = on) } },
                description = "Asks the keyboard not to learn what you type",
                info = InfoText("Incognito keyboard", "Some keyboards ignore the request and learn anyway."),
                icon = Res.drawable.symbol_edit,
            )
        }
    }
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
