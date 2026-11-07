package com.otus.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_edit
import com.otus.designsystem.resources.symbol_image
import com.otus.designsystem.resources.symbol_photo_camera
import com.otus.designsystem.resources.symbol_verified_user
import com.otus.designsystem.resources.symbol_visibility_off
import com.otus.designsystem.shared.feedback.components.InfoText
import com.otus.designsystem.shared.selection.components.ChoiceOptions
import com.otus.designsystem.shared.selection.components.ChoiceRow
import com.otus.designsystem.shared.selection.components.ChoiceSheet
import com.otus.mail.core.messages.domain.DevicePrivacy
import com.otus.mail.core.messages.domain.RemoteImages
import com.otus.mail.core.settings.domain.PhotoCleaning
import com.otus.mail.core.settings.domain.ReadBehavior
import com.otus.mail.core.settings.domain.WriteBehavior
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.core.settings.presentation.allowedSummary
import com.otus.mail.core.settings.presentation.description
import com.otus.mail.core.settings.presentation.title
import com.otus.settings.presentation.components.ChoiceRowSpec
import com.otus.settings.presentation.components.PageRowSpec
import com.otus.settings.presentation.components.SettingRowSpec
import com.otus.settings.presentation.components.SettingsSectionSpec
import com.otus.settings.presentation.components.ToggleRowSpec

/**
 * Settings › Privacy & security: when mail loads images from other servers, chosen in a sheet, who
 * may always show them ([openAllowed] opens that list), what photos you attach keep, the avatars,
 * and the screen and keyboard protections that [device] offers. Each switch and each choice saves
 * at once. Website icons are off by default, so avatars reach only the user's servers.
 */
fun privacySettings(
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
