package com.otus.mail.feature.notifications.domain

import kotlin.time.Duration.Companion.minutes

/**
 * How often Mail checks for new mail where it has no pushes: Android's shortest period for
 * repeating work, which Doze and app standby can stretch.
 */
val PERIODIC_CHECK = 15.minutes

/** A push service, either built into Mail or supplied by an installed app. */
data class PushDistributor(
    val id: String,
    val name: String,
    val isGoogle: Boolean = false,
    val isApple: Boolean = false,
)

/** Instant new-mail pushes through the selected service. */
interface InstantPush {
    val periodicChecks: Boolean get() = true

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
