package com.otus.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otus.avatar.IconSource
import com.otus.avatar.bulwarkBase
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_person
import com.otus.designsystem.resources.symbol_public
import com.otus.designsystem.shared.feedback.components.InfoText
import com.otus.mail.core.settings.presentation.ICONS_UNAVAILABLE
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.settings.presentation.components.ChoiceRowSpec
import com.otus.settings.presentation.components.SettingsSectionSpec
import com.otus.settings.presentation.components.ToggleRowSpec

/**
 * Whether avatars load website icons and contacts from every account. Icons from names where the
 * icons come from and opens [IconSourceSheet]; it stays disabled below the switch while icons are
 * off.
 */
internal fun avatarSettings(state: MailBehaviorState): SettingsSectionSpec {
    val avatars = state.behavior.avatars
    // The only line under the row says why no icons load, since nothing else would.
    val unset = avatars.iconSource == IconSource.Bulwark && bulwarkBase(avatars.bulwarkAddress) == null
    val rows =
        listOf(
            ToggleRowSpec(
                "Website icons",
                avatars.websiteIcons,
                { on -> state.updateAvatars { it.copy(websiteIcons = on) } },
                Res.drawable.symbol_person,
                info = IconSourceInfo,
                keywords = listOf("Favicons", "Logos"),
            ),
            ChoiceRowSpec(
                "Icons from",
                avatars.iconSource.title,
                { IconsFromSheet(state, it) },
                IconSource.entries.map { it.title },
                Res.drawable.symbol_public,
                description = "No Bulwark address, so no icons load".takeIf { unset },
                unavailable = ICONS_UNAVAILABLE.takeUnless { avatars.websiteIcons },
            ),
            ToggleRowSpec(
                "Photos from all accounts",
                avatars.allAccountPhotos,
                { on -> state.updateAvatars { it.copy(allAccountPhotos = on) } },
                Res.drawable.symbol_person,
                info = AllPhotosInfo,
            ),
        )
    return SettingsSectionSpec("Avatars", rows)
}

@Composable
private fun IconsFromSheet(
    state: MailBehaviorState,
    onDismiss: () -> Unit,
) {
    val avatars = state.behavior.avatars
    IconSourceSheet(
        avatars.iconSource,
        avatars.bulwarkAddress,
        onSave = { source, address -> state.updateAvatars { it.copy(iconSource = source, bulwarkAddress = address) } },
        onDismiss = onDismiss,
    )
}

private val AllPhotosInfo =
    InfoText(
        "Photos from all accounts",
        "Mail to one of your accounts shows a contact's photo even when another account saved the contact.",
    )

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
