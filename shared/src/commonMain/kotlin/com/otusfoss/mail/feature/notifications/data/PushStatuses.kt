package com.otusfoss.mail.feature.notifications.data

import com.otusfoss.core.jmap.push.PushStatus
import com.otusfoss.mail.feature.notifications.domain.PushDeliveryStatus

/** How a login's push subscription shows in Settings › Notifications; none yet is still connecting. */
val PushStatus?.delivery: PushDeliveryStatus
    get() =
        when (this) {
            PushStatus.Active -> PushDeliveryStatus.Active
            PushStatus.Refused -> PushDeliveryStatus.ServerRefused
            PushStatus.Off, PushStatus.Waiting, null -> PushDeliveryStatus.Connecting
        }
