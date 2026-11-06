package com.otus.mail.app

import com.otus.mail.core.messages.domain.DevicePrivacy
import kotlin.time.Instant

/**
 * What the platform the app runs on offers [MailServices]: how [nightly] builds install, and which
 * screen and keyboard protections, [devicePrivacy], it can apply. [wakeOutbox] runs
 * [MailServices.resumeOutbox] at a time, even with the app closed, where the platform can.
 */
class MailPlatform(
    internal val nightly: NightlyUpdates = NightlyUpdates(),
    internal val devicePrivacy: DevicePrivacy = DevicePrivacy(),
    internal val wakeOutbox: (Instant) -> Unit = {},
)
