package com.otus.mail.core.avatar

import com.otus.avatar.AvatarOptions

/**
 * Sender avatars always look up contact photos on the user's own servers. [websiteIcons] also lets
 * them load photos that contact cards link to and icons from sender websites, and [duckDuckGo]
 * asks DuckDuckGo's icon cache for a site without one; it counts only while [websiteIcons] is on.
 * Each of those servers sees the device's IP address.
 */
fun mailAvatarOptions(
    websiteIcons: Boolean,
    duckDuckGo: Boolean = false,
): AvatarOptions =
    AvatarOptions(
        externalContactImages = websiteIcons,
        domainIcons = websiteIcons,
        domainIconProxy = websiteIcons && duckDuckGo,
    )
