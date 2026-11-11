package com.otusfoss.mail.core.settings.domain

import com.otusfoss.core.avatar.IconSource

/**
 * Where sender avatars come from. [websiteIcons] lets them load images from outside the user's
 * servers, website icons from [iconSource], and with Bulwark from the server at [bulwarkAddress].
 * [allAccountPhotos] lets mail to one account show the photo of a contact saved in another.
 */
data class AvatarBehavior(
    val websiteIcons: Boolean = false,
    val iconSource: IconSource = IconSource.Websites,
    val bulwarkAddress: String = "",
    val allAccountPhotos: Boolean = true,
)
