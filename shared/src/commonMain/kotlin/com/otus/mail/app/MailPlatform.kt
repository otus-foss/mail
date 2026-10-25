package com.otus.mail.app

import com.otus.mail.feature.inbox.domain.DevicePrivacy

/**
 * What the platform the app runs on offers [MailServices]: how [nightly] builds install, and which
 * screen and keyboard protections, [devicePrivacy], it can apply.
 */
class MailPlatform(
    internal val nightly: NightlyUpdates = NightlyUpdates(),
    internal val devicePrivacy: DevicePrivacy = DevicePrivacy(),
)
