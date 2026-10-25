package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.InfoText
import com.otus.designsystem.components.PickMark
import com.otus.designsystem.components.PickRow
import com.otus.designsystem.components.SettingPageRow
import com.otus.designsystem.components.SettingToggleRow
import com.otus.designsystem.components.SettingsSection
import com.otus.designsystem.components.Subpage
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_edit
import com.otus.designsystem.resources.symbol_person
import com.otus.designsystem.resources.symbol_verified_user
import com.otus.designsystem.resources.symbol_visibility_off
import com.otus.mail.feature.inbox.domain.DevicePrivacy
import com.otus.mail.feature.inbox.domain.PhotoCleaning
import com.otus.mail.feature.inbox.domain.RemoteImages
import com.otus.mail.feature.inbox.presentation.MailBehaviorState

/**
 * Settings › Privacy: when mail loads images from other servers, who may always show them
 * ([onOpenAllowed] opens that list), whether avatars load website icons, and the screen and
 * keyboard protections that [device] offers. Each change is saved at once. Website icons and
 * DuckDuckGo are off by default, so avatars reach only the user's servers.
 */
@Composable
fun PrivacyScreen(
    state: MailBehaviorState,
    onOpenAllowed: () -> Unit,
    onBack: () -> Unit,
    device: DevicePrivacy = DevicePrivacy(),
) {
    val behavior = state.behavior
    Subpage("Privacy", onBack) {
        SettingsSection("Load remote images", info = ImagesInfo) {
            RemoteImages.entries.forEach { choice ->
                ImagesChoiceRow(choice, selected = choice == behavior.remoteImages) {
                    state.update { it.copy(remoteImages = choice) }
                }
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
            PhotoCleaning.entries.forEach { choice ->
                PickRow(
                    choice.title,
                    choice.description,
                    PickMark(choice == behavior.photoCleaning, atStart = true),
                    { state.update { it.copy(photoCleaning = choice) } },
                    subtitleLines = DESCRIPTION_LINES,
                )
            }
        }
        SettingsSection("Avatars") {
            SettingToggleRow(
                "Website icons",
                checked = behavior.websiteIcons,
                onCheckedChange = { on -> state.update { it.copy(websiteIcons = on) } },
                description = "Logos from sender websites",
                icon = Res.drawable.symbol_person,
                info =
                    InfoText(
                        "Website icons",
                        "The sender's website sees your IP address and that you got their mail.",
                    ),
            )
            if (behavior.websiteIcons) {
                SettingToggleRow(
                    "Ask DuckDuckGo",
                    checked = behavior.duckDuckGoIcons,
                    onCheckedChange = { on -> state.update { it.copy(duckDuckGoIcons = on) } },
                    description = "For sites without an icon",
                    icon = Res.drawable.symbol_person,
                    info = InfoText("Ask DuckDuckGo", "DuckDuckGo sees your IP address and each sender's domain."),
                )
            }
        }
        if (device.screen || device.keyboard) {
            SettingsSection("This device") {
                if (device.screen) {
                    SettingToggleRow(
                        "Screen security",
                        checked = behavior.screenSecurity,
                        onCheckedChange = { on -> state.update { it.copy(screenSecurity = on) } },
                        description = "Hides Mail from recent apps and screenshots",
                        info =
                            InfoText(
                                "Screen security",
                                "Mail is hidden in recent apps, and screenshots and screen recording are blocked.",
                            ),
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
    }
}

/** One of the three image choices: a radio first, then its name and what it means. */
@Composable
private fun ImagesChoiceRow(
    choice: RemoteImages,
    selected: Boolean,
    onClick: () -> Unit,
) {
    PickRow(
        choice.title,
        choice.description,
        PickMark(selected, atStart = true),
        onClick,
        subtitleLines = DESCRIPTION_LINES,
    )
}

/** Enough lines for the longest description on a narrow phone. */
private const val DESCRIPTION_LINES = 2

private val RemoteImages.title: String
    get() =
        when (this) {
            RemoteImages.Always -> "Always"
            RemoteImages.Known -> "From people you know"
            RemoteImages.Never -> "Never"
        }

private val RemoteImages.description: String
    get() =
        when (this) {
            RemoteImages.Always -> {
                "Senders can see when you open their mail"
            }

            RemoteImages.Known -> {
                "Contacts and senders you allowed"
            }

            RemoteImages.Never -> {
                "Always ask"
            }
        }

private val PhotoCleaning.title: String
    get() =
        when (this) {
            PhotoCleaning.Ask -> "Ask each time"
            PhotoCleaning.Always -> "Always remove details"
            PhotoCleaning.Never -> "Keep details"
        }

private val PhotoCleaning.description: String
    get() =
        when (this) {
            PhotoCleaning.Ask -> {
                "Choose for each photo"
            }

            PhotoCleaning.Always -> {
                "Removes location, time, and camera"
            }

            PhotoCleaning.Never -> {
                "Photos go out as they are"
            }
        }

private val ImagesInfo =
    InfoText(
        "Remote images",
        listOf(
            "Always shows every mail as the sender designed it, and lets senders see when you open their mail.",
            "From people you know loads images from contacts, people you've written to, and senders you " +
                "allowed. Everyone else asks first.",
            "Images that are part of the mail itself always show. Mail in Spam never loads remote images.",
        ),
    )

private val PhotosInfo =
    InfoText(
        "Photo details",
        listOf(
            "Photos can carry where, when, and with what camera they were taken, and their file name.",
            "Always remove details strips these from every JPEG, PNG, and WebP photo. Ask each time offers " +
                "removing them first. Keep details sends photos as they are.",
        ),
    )
