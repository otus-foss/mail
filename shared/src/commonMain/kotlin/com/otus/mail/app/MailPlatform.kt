package com.otus.mail.app

import com.otus.mail.core.messages.domain.DevicePrivacy
import com.otus.mail.feature.notifications.domain.InstantPush
import kotlin.time.Instant

/**
 * What the platform the app runs on offers [MailServices]: how [nightly] builds install, and which
 * screen and keyboard protections, [devicePrivacy], it can apply. [wakeOutbox] runs
 * [MailServices.resumeOutbox] at a time, even with the app closed, where the platform can.
 * [instantPush] delivers pushes through the selected service; null where the platform has none.
 * [categoriesOn] reads whether the user has Inbox categories on, from the settings the process
 * keeps, so lists sort before any screen shows; see docs/categories.md.
 */
class MailPlatform(
    internal val nightly: NightlyUpdates = NightlyUpdates(),
    internal val devicePrivacy: DevicePrivacy = DevicePrivacy(),
    internal val wakeOutbox: (Instant) -> Unit = {},
    internal val instantPush: InstantPush? = null,
    internal val categoriesOn: () -> Boolean = { false },
)
