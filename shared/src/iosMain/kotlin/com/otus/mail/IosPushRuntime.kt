package com.otus.mail

import com.otus.mail.app.ApplePushRuntime
import com.otus.mail.feature.notifications.data.ApplePushPlatform

class IosPushRuntime(
    platform: ApplePushPlatform,
    baseUrl: String,
) : ApplePushRuntime(platform, baseUrl, ::iosMailServices)
