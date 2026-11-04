package com.otus.mail.feature.inbox.presentation.components

import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_edit
import com.otus.designsystem.resources.symbol_image
import com.otus.designsystem.resources.symbol_person
import com.otus.designsystem.resources.symbol_photo_camera
import com.otus.designsystem.resources.symbol_public
import com.otus.designsystem.resources.symbol_verified_user
import com.otus.designsystem.resources.symbol_visibility_off
import com.otus.mail.core.avatar.IconSource
import com.otus.mail.feature.inbox.domain.DevicePrivacy
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.PhotoCleaning
import com.otus.mail.feature.inbox.domain.RemoteImages
import com.otus.mail.feature.inbox.presentation.ICONS_UNAVAILABLE
import com.otus.mail.feature.inbox.presentation.allowedSummary
import com.otus.mail.feature.inbox.presentation.title
import com.otus.settings.presentation.SettingsIndexScope

/** Mirrors PrivacyScreen, with the device protections [device] offers. */
fun SettingsIndexScope.privacyIndex(
    behavior: MailBehavior,
    device: DevicePrivacy,
    openAllowed: (String?) -> Unit,
) {
    section("Load remote images") {
        setting(
            "Load images",
            behavior.remoteImages.title,
            RemoteImages.entries.map { it.title },
            Res.drawable.symbol_image,
            keywords = listOf("Remote content", "Tracking pixels"),
        )
        page(
            "Allowed senders",
            openAllowed,
            Res.drawable.symbol_verified_user,
            value = allowedSummary(behavior.allowedSenders.size, behavior.remoteImages),
        )
    }
    section("Photos you attach") {
        setting(
            "Photo details",
            behavior.photoCleaning.title,
            PhotoCleaning.entries.map { it.title },
            Res.drawable.symbol_photo_camera,
            keywords = listOf("Location", "Metadata", "EXIF"),
        )
    }
    section("Avatars") {
        val icons = listOf("Favicons", "Logos")
        toggle("Website icons", behavior.websiteIcons, Res.drawable.symbol_person, keywords = icons)
        unavailable(ICONS_UNAVAILABLE.takeUnless { behavior.websiteIcons }) {
            val sources = IconSource.entries.map { it.title }
            setting("Icons from", behavior.iconSource.title, sources, Res.drawable.symbol_public)
        }
        toggle("Photos from all accounts", behavior.allAccountPhotos, Res.drawable.symbol_person)
    }
    section("This device") {
        if (device.screen) {
            toggle(
                "Screen security",
                behavior.screenSecurity,
                Res.drawable.symbol_visibility_off,
                keywords = listOf("Screenshots", "Recent apps"),
            )
        }
        if (device.keyboard) {
            toggle(
                "Incognito keyboard",
                behavior.incognitoKeyboard,
                Res.drawable.symbol_edit,
                keywords = listOf("Typing", "Learning"),
            )
        }
    }
}
