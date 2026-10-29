package com.otus.mail

import com.otus.jmap.JmapException
import com.otus.mail.feature.notifications.domain.PushDeliveryStatus
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import com.otus.jmap.PushMessage as JmapPush

/**
 * Receives what the UnifiedPush app sends for each account; the instance is the login id. Work
 * that needs the network goes to WorkManager, because the system may stop this service any time.
 */
class MailPushService : PushService() {
    override fun onNewEndpoint(
        endpoint: PushEndpoint,
        instance: String,
    ) {
        val keys = endpoint.pubKeySet
        PushSyncWorker.enqueue(this, PushSyncWorker.endpoint(instance, endpoint.url, keys?.pubKey, keys?.auth))
    }

    override fun onMessage(
        message: PushMessage,
        instance: String,
    ) {
        when (val push = decode(message)) {
            is JmapPush.StateChange -> {
                NewMailWorker.runNow(this)
            }

            is JmapPush.Verification -> {
                verify(instance, push)
            }

            null -> {}
        }
    }

    private fun verify(
        instance: String,
        push: JmapPush.Verification,
    ) {
        PushSyncWorker.enqueue(this, PushSyncWorker.verification(instance, push))
    }

    override fun onUnregistered(instance: String) {
        PushSyncWorker.enqueue(this, PushSyncWorker.cleared(instance))
    }

    override fun onRegistrationFailed(
        reason: FailedReason,
        instance: String,
    ) {
        val problem =
            when (reason) {
                FailedReason.VAPID_REQUIRED -> PushDeliveryStatus.VapidRequired
                FailedReason.NETWORK -> PushDeliveryStatus.Unavailable
                FailedReason.INTERNAL_ERROR, FailedReason.ACTION_REQUIRED -> PushDeliveryStatus.RegistrationFailed
            }
        PushSyncWorker.enqueue(this, PushSyncWorker.failed(instance, problem))
    }

    /**
     * Only pushes encrypted with this endpoint's keys (RFC 8291) are read. Anyone who learns the
     * endpoint URL can post to it, but only the server holds the keys, and Mail never subscribes
     * without them.
     */
    private fun decode(message: PushMessage): JmapPush? =
        try {
            if (message.decrypted) JmapPush.decode(message.content.decodeToString()) else null
        } catch (_: JmapException) {
            null
        }
}
