package com.otus.mail

import com.otus.mail.feature.notifications.domain.InstantPush
import com.otus.mail.feature.notifications.domain.PushDeliveryStatus
import com.otus.mail.feature.notifications.domain.PushDistributor

internal class FakeNotificationPush(
    val installed: List<PushDistributor>,
    override val periodicChecks: Boolean = true,
) : InstantPush {
    var chosen: PushDistributor? = null
    var status = PushDeliveryStatus.Connecting

    override suspend fun distributors() = installed

    override suspend fun current() = chosen

    override suspend fun use(distributor: PushDistributor?) {
        chosen = distributor
    }

    override suspend fun statuses() = if (chosen == null) emptyMap() else mapOf("ada" to status)

    override suspend fun forget(loginId: String) = Unit
}
