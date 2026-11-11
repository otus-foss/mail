package com.otusfoss.mail.core.settings.domain

import com.otusfoss.core.avatar.IconSource
import com.otusfoss.mail.core.messages.domain.RemoteImages

/**
 * A set of privacy settings to start from, which the first run and Settings › Privacy & security
 * offer. [Private] loads nothing from outside the user's servers and keeps mail off the screen
 * where it can; [Convenient] loads what makes mail easier to read. [hidesAlertContent] is whether
 * new-mail alerts show only "New mail"; the notifications feature keeps that setting.
 */
enum class PrivacyPreset(
    val hidesAlertContent: Boolean,
) {
    Private(hidesAlertContent = true),
    Convenient(hidesAlertContent = false),
    ;

    /**
     * [behavior] with this preset's settings filled in; every other setting stays as it is.
     * [Private] turns website icons off without changing where they come from, so a Bulwark
     * server the user set up is still chosen when they turn icons back on.
     */
    fun applyTo(behavior: MailBehavior): MailBehavior =
        when (this) {
            Private -> {
                behavior.copy(
                    read = behavior.read.copy(remoteImages = RemoteImages.Never),
                    write = behavior.write.copy(photoCleaning = PhotoCleaning.Always),
                    avatars = behavior.avatars.copy(websiteIcons = false, allAccountPhotos = false),
                    privacy = behavior.privacy.copy(screenSecurity = true, incognitoKeyboard = true),
                )
            }

            Convenient -> {
                behavior.copy(
                    read = behavior.read.copy(remoteImages = RemoteImages.Known),
                    write = behavior.write.copy(photoCleaning = PhotoCleaning.Ask),
                    avatars =
                        behavior.avatars.copy(
                            websiteIcons = true,
                            iconSource = IconSource.DuckDuckGo,
                            allAccountPhotos = true,
                        ),
                    privacy = behavior.privacy.copy(screenSecurity = false, incognitoKeyboard = true),
                )
            }
        }
}

/** The preset whose settings [this] has now, or null once the user has changed one of them. */
fun MailBehavior.privacyPreset(): PrivacyPreset? = PrivacyPreset.entries.firstOrNull { it.applyTo(this) == this }
