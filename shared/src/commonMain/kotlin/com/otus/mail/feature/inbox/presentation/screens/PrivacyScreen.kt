package com.otus.mail.feature.inbox.presentation.screens

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
import com.otus.designsystem.shared.selection.components.RadioChoiceSheet
import com.otus.designsystem.shared.settings.components.SettingPageRow
import com.otus.designsystem.shared.settings.components.SettingRow
import com.otus.designsystem.shared.settings.components.SettingToggleRow
import com.otus.designsystem.shared.settings.components.SettingsSection
import com.otus.designsystem.shared.settings.elements.ValueButton
import com.otus.mail.feature.inbox.domain.DevicePrivacy
import com.otus.mail.feature.inbox.domain.PhotoCleaning
import com.otus.mail.feature.inbox.domain.RemoteImages
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.components.AvatarSettings

/**
 * Settings › Privacy: when mail loads images from other servers, chosen in a sheet, who may always
 * show them ([onOpenAllowed] opens that list), whether avatars load website icons, and the screen
 * and keyboard protections that [device] offers. Each switch saves at once, and each sheet saves
 * on Done. Website icons are off by default, so avatars reach only the user's servers.
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
    Subpage("Privacy", onBack) {
        SettingsSection("Load remote images", info = ImagesInfo) {
            SettingRow("Load images", { choosingImages = true }, icon = Res.drawable.symbol_image) {
                ValueButton(behavior.remoteImages.title)
            }
            val count = behavior.allowedSenders.size
            SettingPageRow(
                "Allowed senders",
                onOpenAllowed,
                description = if (count == 1) "1 sender" else "$count senders",
                icon = Res.drawable.symbol_verified_user,
            )
        }
        SettingsSection("Photos you attach", info = PhotosInfo) {
            SettingRow("Photo details", { choosingPhotos = true }, icon = Res.drawable.symbol_photo_camera) {
                ValueButton(behavior.photoCleaning.title)
            }
        }
        AvatarSettings(state)
        if (device.screen || device.keyboard) DeviceSection(state, device)
    }
    if (choosingImages) {
        RadioChoiceSheet(
            "Load remote images",
            ChoiceOptions(RemoteImages.entries, behavior.remoteImages),
            row = { ChoiceRow(it.title, it.description) },
            onSave = { choice -> state.update { it.copy(remoteImages = choice) } },
            onDismiss = { choosingImages = false },
        )
    }
    if (choosingPhotos) {
        RadioChoiceSheet(
            "Photos you attach",
            ChoiceOptions(PhotoCleaning.entries, behavior.photoCleaning),
            row = { ChoiceRow(it.title, it.description) },
            onSave = { choice -> state.update { it.copy(photoCleaning = choice) } },
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
    val behavior = state.behavior
    SettingsSection("This device") {
        if (device.screen) {
            SettingToggleRow(
                "Screen security",
                checked = behavior.screenSecurity,
                onCheckedChange = { on -> state.update { it.copy(screenSecurity = on) } },
                description = "Hides Mail from recent apps and screenshots",
                icon = Res.drawable.symbol_visibility_off,
            )
        }
        if (device.keyboard) {
            SettingToggleRow(
                "Incognito keyboard",
                checked = behavior.incognitoKeyboard,
                onCheckedChange = { on -> state.update { it.copy(incognitoKeyboard = on) } },
                description = "Asks the keyboard not to learn what you type",
                info = InfoText("Incognito keyboard", "Some keyboards ignore the request and learn anyway."),
                icon = Res.drawable.symbol_edit,
            )
        }
    }
}

internal val RemoteImages.title: String
    get() =
        when (this) {
            RemoteImages.Always -> "Always"
            RemoteImages.Known -> "In your contacts"
            RemoteImages.Never -> "Never"
        }

private val RemoteImages.description: String
    get() =
        when (this) {
            RemoteImages.Always -> "Senders can see when you open their mail"
            RemoteImages.Known -> "Contacts and senders you allowed"
            RemoteImages.Never -> "Always ask"
        }

internal val PhotoCleaning.title: String
    get() =
        when (this) {
            PhotoCleaning.Ask -> "Ask each time"
            PhotoCleaning.Always -> "Always remove details"
            PhotoCleaning.Never -> "Keep details"
        }

private val PhotoCleaning.description: String
    get() =
        when (this) {
            PhotoCleaning.Ask -> "Choose for each photo"
            PhotoCleaning.Always -> "Removes location, time, and camera"
            PhotoCleaning.Never -> "Photos go out as they are"
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
