package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.PageNote
import com.otus.designsystem.components.PickMark
import com.otus.designsystem.components.PickRow
import com.otus.designsystem.components.SettingPageRow
import com.otus.designsystem.components.SettingToggleRow
import com.otus.designsystem.components.SettingsSection
import com.otus.designsystem.components.Subpage
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_person
import com.otus.designsystem.resources.symbol_verified_user
import com.otus.designsystem.theme.PageTokens
import com.otus.mail.feature.inbox.domain.RemoteImages
import com.otus.mail.feature.inbox.presentation.MailBehaviorState

/**
 * Settings › Privacy: when mail loads images from other servers, who may always show them
 * ([onOpenAllowed] opens that list), and whether avatars load website icons. Each change is saved
 * at once. Website icons and DuckDuckGo are off by default, so avatars reach only the user's servers.
 */
@Composable
fun PrivacyScreen(
    state: MailBehaviorState,
    onOpenAllowed: () -> Unit,
    onBack: () -> Unit,
) {
    val behavior = state.behavior
    Subpage("Privacy", onBack) {
        SettingsSection("Load remote images") {
            RemoteImages.entries.forEach { choice ->
                ImagesChoiceRow(choice, selected = choice == behavior.remoteImages) {
                    state.update { it.copy(remoteImages = choice) }
                }
            }
            Box(Modifier.padding(vertical = PageTokens.rowGap)) {
                PageNote("Images that are part of the mail itself always show. Mail in Spam never loads remote images.")
            }
            val count = behavior.allowedSenders.size
            SettingPageRow(
                "Allowed senders",
                onOpenAllowed,
                description = if (count == 1) "1 sender" else "$count senders",
                icon = Res.drawable.symbol_verified_user,
            )
        }
        SettingsSection("Avatars") {
            SettingToggleRow(
                "Website icons",
                checked = behavior.websiteIcons,
                onCheckedChange = { on -> state.update { it.copy(websiteIcons = on) } },
                description = "Logos from sender websites, which see your IP address and that you got their mail",
                icon = Res.drawable.symbol_person,
            )
            if (behavior.websiteIcons) {
                SettingToggleRow(
                    "Ask DuckDuckGo",
                    checked = behavior.duckDuckGoIcons,
                    onCheckedChange = { on -> state.update { it.copy(duckDuckGoIcons = on) } },
                    description = "For sites without an icon. DuckDuckGo sees your IP address and each sender's domain",
                    icon = Res.drawable.symbol_person,
                )
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
private const val DESCRIPTION_LINES = 4

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
                "Every mail looks as the sender designed it. Senders can see when you open their mail."
            }

            RemoteImages.Known -> {
                "Contacts, people you've written to and senders you allowed. Everyone else asks first."
            }

            RemoteImages.Never -> {
                "Always ask, for every mail."
            }
        }
