package com.otus.mail

import com.otus.mail.app.MailServices
import com.otus.mail.feature.notifications.data.ApplePushPlatform
import com.otus.mail.feature.notifications.data.ApplePushRuntime

class IosPushRuntime(
    platform: ApplePushPlatform,
    baseUrl: String,
) : ApplePushRuntime<MailServices>(platform, baseUrl, ::iosMailServices)
