package com.otus.mail.feature.notifications.domain

import com.otus.push.PushStatus

/** An installed UnifiedPush app, such as ntfy, that can deliver pushes to Mail. */
data class PushDistributor(
    val id: String,
    val name: String,
)

/** Instant new-mail pushes through the user's UnifiedPush app. */
interface InstantPush {
    suspend fun distributors(): List<PushDistributor>

    /** The app in use, or null when Mail only checks periodically. */
    suspend fun current(): PushDistributor?

    /** Switches to [distributor], or to periodic checks only with null. Accounts register in the background. */
    suspend fun use(distributor: PushDistributor?)

    /** Each login's push status, keyed by login id. */
    suspend fun statuses(): Map<String, PushStatus>
}
