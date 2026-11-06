package com.otus.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.avatar.IconSource
import com.otus.avatar.bulwarkBase
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_person
import com.otus.designsystem.resources.symbol_public
import com.otus.designsystem.shared.feedback.components.InfoText
import com.otus.designsystem.shared.settings.components.DependentSettings
import com.otus.designsystem.shared.settings.components.SettingRow
import com.otus.designsystem.shared.settings.components.SettingToggleRow
import com.otus.designsystem.shared.settings.components.SettingsSection
import com.otus.designsystem.shared.settings.elements.ValueButton
import com.otus.mail.core.settings.presentation.ICONS_UNAVAILABLE
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.core.settings.presentation.icon

/**
 * Whether avatars load website icons and contacts from every account. Icons from names where the
 * icons come from and opens [IconSourceSheet]; it stays disabled below the switch while icons are
 * off.
 */
@Composable
internal fun AvatarSettings(state: MailBehaviorState) {
    val avatars = state.behavior.avatars
    var choosing by rememberSaveable { mutableStateOf(false) }
    SettingsSection("Avatars") {
        SettingToggleRow(
            "Website icons",
            checked = avatars.websiteIcons,
            onCheckedChange = { on -> state.updateAvatars { it.copy(websiteIcons = on) } },
            icon = Res.drawable.symbol_person,
            info = IconSourceInfo,
        )
        // The only line under the row says why no icons load, since nothing else would.
        val unset = avatars.iconSource == IconSource.Bulwark && bulwarkBase(avatars.bulwarkAddress) == null
        DependentSettings(enabled = avatars.websiteIcons) {
            SettingRow(
                "Icons from",
                { choosing = true },
                description = iconsNote(avatars.websiteIcons, unset),
                icon = Res.drawable.symbol_public,
            ) { ValueButton(avatars.iconSource.title) }
        }
        SettingToggleRow(
            "Photos from all accounts",
            checked = avatars.allAccountPhotos,
            onCheckedChange = { on -> state.updateAvatars { it.copy(allAccountPhotos = on) } },
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
            avatars.iconSource,
            avatars.bulwarkAddress,
            onSave = { source, address ->
                state.updateAvatars { it.copy(iconSource = source, bulwarkAddress = address) }
            },
            onDismiss = { choosing = false },
        )
    }
}

private fun iconsNote(
    on: Boolean,
    unset: Boolean,
): String? =
    when {
        !on -> ICONS_UNAVAILABLE
        unset -> "No Bulwark address, so no icons load"
        else -> null
    }

private val IconSourceInfo =
    InfoText(
        "Website icons",
        listOf(
            "Senders without a contact photo show their website's logo.",
            "Sender websites give the sharpest icons, but each website sees your IP address and that you " +
                "got its mail.",
            "DuckDuckGo fetches icons for you, so senders see nothing. DuckDuckGo sees your IP address and " +
                "each sender's domain.",
            "Bulwark asks your own Bulwark webmail server, so only your server sees your IP address. It also " +
                "shows brand logos for mail that passed DMARC.",
        ),
    )
