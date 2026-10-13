package com.otus.mail.core.avatar

import com.otus.avatar.AvatarOptions

/**
 * Sender avatars always look up contact photos on the user's own servers. [websiteIcons] also lets
 * them load images from anywhere else: photos that contact cards link to, icons from sender
 * websites, and DuckDuckGo's icon cache. Each of those servers sees the device's IP address.
 */
fun mailAvatarOptions(websiteIcons: Boolean): AvatarOptions =
    AvatarOptions(
        externalContactImages = websiteIcons,
        domainIcons = websiteIcons,
        domainIconProxy = websiteIcons,
    )
