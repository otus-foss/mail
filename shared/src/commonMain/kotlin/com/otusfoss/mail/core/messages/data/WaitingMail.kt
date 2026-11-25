package com.otusfoss.mail.core.messages.data

import com.otusfoss.mail.core.messages.domain.DeviceDrafts

/**
 * What waits on the device for a login's server and goes before its mail loads: marks and moves in
 * [changes], and the drafts written offline in [drafts], where the app keeps any.
 */
class WaitingMail(
    val changes: EmailChangeQueue,
    val drafts: DeviceDrafts? = null,
)
