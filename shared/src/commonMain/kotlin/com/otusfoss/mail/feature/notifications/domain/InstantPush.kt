package com.otusfoss.mail.feature.notifications.domain

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

/**
 * The service a first run starts with: an installed app's when the user chose [private] settings,
 * so pushes do not pass through Google, and otherwise Google's where the device offers it. Null
 * leaves Mail on periodic checks.
 */
fun List<PushDistributor>.suggested(private: Boolean): PushDistributor? =
    if (private) firstOrNull { !it.isGoogle } else firstOrNull { it.isGoogle } ?: firstOrNull()

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
