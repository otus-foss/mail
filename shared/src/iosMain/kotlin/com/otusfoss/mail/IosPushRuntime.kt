package com.otusfoss.mail

import com.otusfoss.mail.app.MailServices
import com.otusfoss.mail.feature.notifications.data.ApplePushPlatform
import com.otusfoss.mail.feature.notifications.data.ApplePushRuntime

class IosPushRuntime(
    platform: ApplePushPlatform,
    baseUrl: String,
) : ApplePushRuntime<MailServices>(platform, baseUrl, ::iosMailServices)
