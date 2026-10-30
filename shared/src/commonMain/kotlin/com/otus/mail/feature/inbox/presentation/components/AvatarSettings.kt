package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_person
import com.otus.designsystem.resources.symbol_public
import com.otus.designsystem.shared.feedback.components.InfoText
import com.otus.designsystem.shared.settings.components.SettingRow
import com.otus.designsystem.shared.settings.components.SettingToggleRow
import com.otus.designsystem.shared.settings.components.SettingsSection
import com.otus.designsystem.shared.settings.elements.ValueButton
import com.otus.mail.core.avatar.IconSource
import com.otus.mail.core.avatar.bulwarkBase
import com.otus.mail.feature.inbox.presentation.MailBehaviorState

/**
 * Whether avatars load website icons and contacts from every account. While icons are on, Icons
 * from names where they come from and who sees the device, and opens [IconSourceSheet].
 */
@Composable
internal fun AvatarSettings(state: MailBehaviorState) {
    val behavior = state.behavior
    var choosing by rememberSaveable { mutableStateOf(false) }
    SettingsSection("Avatars") {
        SettingToggleRow(
            "Website icons",
            checked = behavior.websiteIcons,
            onCheckedChange = { on -> state.update { it.copy(websiteIcons = on) } },
            icon = Res.drawable.symbol_person,
            info = IconSourceInfo,
        )
        if (behavior.websiteIcons) {
            // The only line under the row says why no icons load, since nothing else would.
            val unset = behavior.iconSource == IconSource.Bulwark && bulwarkBase(behavior.bulwarkAddress) == null
            SettingRow(
                "Icons from",
                { choosing = true },
                description = "No Bulwark address, so no icons load".takeIf { unset },
                icon = Res.drawable.symbol_public,
            ) { ValueButton(behavior.iconSource.title) }
        }
        SettingToggleRow(
            "Photos from all accounts",
            checked = behavior.allAccountPhotos,
            onCheckedChange = { on -> state.update { it.copy(allAccountPhotos = on) } },
            icon = Res.drawable.symbol_person,
            info =
                InfoText(
                    "Photos from all accounts",
                    "Mail to one of your accounts shows a contact's photo even when another account saved the contact.",
                ),
        )
    }
    if (choosing) {
        IconSourceSheet(
            behavior.iconSource,
            behavior.bulwarkAddress,
            onSave = { source, address -> state.update { it.copy(iconSource = source, bulwarkAddress = address) } },
            onDismiss = { choosing = false },
        )
    }
}

private val IconSourceInfo =
    InfoText(
        "Website icons",
        listOf(
            "Senders without a contact photo show their website's logo. Icons from picks who fetches it.",
            "Sender websites give the sharpest icons, but each website sees your IP address and that you " +
                "got its mail.",
            "DuckDuckGo fetches icons for you, so senders see nothing. DuckDuckGo sees your IP address and " +
                "each sender's domain.",
            "Bulwark asks your own Bulwark webmail server, so only your server sees your IP address. It also " +
                "shows brand logos for mail that passed DMARC.",
        ),
    )
