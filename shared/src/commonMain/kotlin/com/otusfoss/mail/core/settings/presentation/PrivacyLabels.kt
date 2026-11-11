package com.otusfoss.mail.core.settings.presentation

import com.otusfoss.mail.core.messages.domain.RemoteImages
import com.otusfoss.mail.core.settings.domain.PhotoCleaning
import com.otusfoss.mail.core.settings.domain.PrivacyPreset

internal val RemoteImages.title: String
    get() =
        when (this) {
            RemoteImages.Always -> "Always"
            RemoteImages.Known -> "In your contacts"
            RemoteImages.Never -> "Never"
        }

internal val RemoteImages.description: String
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

internal val PhotoCleaning.description: String
    get() =
        when (this) {
            PhotoCleaning.Ask -> "Choose for each photo"
            PhotoCleaning.Always -> "Removes location, time, and camera"
            PhotoCleaning.Never -> "Photos go out as they are"
        }

/** The privacy page's title, which the settings home and search also show. */
const val PRIVACY_TITLE = "Privacy & security"

/**
 * The Allowed senders row's summary: how many there are, and that the list only counts while
 * [images] load for contacts. The row stays open, so senders can still be taken out.
 */
fun allowedSummary(
    count: Int,
    images: RemoteImages,
): String {
    val senders = if (count == 1) "1 sender" else "$count senders"
    return if (images == RemoteImages.Known) senders else "$senders, used only with In your contacts"
}

/** Why Icons from is disabled while Website icons is off; search shows it too. */
const val ICONS_UNAVAILABLE = "Turn on Website icons to choose"

internal val PrivacyPreset.title: String
    get() =
        when (this) {
            PrivacyPreset.Private -> "Full privacy"
            PrivacyPreset.Convenient -> "Convenient"
        }

/** What the preset does, in one line. */
internal val PrivacyPreset.summary: String
    get() =
        when (this) {
            PrivacyPreset.Private -> "No images or icons load. Alerts hide the sender."
            PrivacyPreset.Convenient -> "Images from people you know, sender icons, full alerts."
        }
