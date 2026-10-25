package com.otus.mail.feature.notifications.domain

/** A push service, either built into Mail or supplied by an installed app. */
data class PushDistributor(
    val id: String,
    val name: String,
    val isGoogle: Boolean = false,
)

/** Instant new-mail pushes through the selected service. */
interface InstantPush {
    suspend fun distributors(): List<PushDistributor>

    /** The app in use, or null when Mail only checks periodically. */
    suspend fun current(): PushDistributor?

    /** Switches to [distributor], or to periodic checks only with null. Accounts register in the background. */
    suspend fun use(distributor: PushDistributor?)

    /** Each login's push status, keyed by login id. */
    suspend fun statuses(): Map<String, PushDeliveryStatus>

    /** Stops pushes for a login that is being removed, while its credentials still exist. */
    suspend fun forget(loginId: String)
}
