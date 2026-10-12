package com.otus.mail.core.avatar

import com.otus.avatar.AvatarOptions

/** Every sender avatar in Mail looks up contact photos, then website icons through the proxy. */
val MailAvatarOptions: AvatarOptions =
    AvatarOptions(
        externalContactImages = true,
        domainIcons = true,
        domainIconProxy = true,
    )
