package com.otusfoss.mail.core.messages.domain

/** When the Inbox downloads the bodies of its first rows ahead of their opening. See Settings › Mail behavior. */
enum class BodyPrefetch { Always, Unmetered, Never }

/**
 * What the device says its network costs: whether it is [metered], such as mobile data, and
 * whether the user's [dataSaver] asks apps to hold back in the background.
 */
interface NetworkCost {
    val metered: Boolean
    val dataSaver: Boolean

    /** A device that reports no cost, so nothing holds a download back. */
    object Free : NetworkCost {
        override val metered = false
        override val dataSaver = false
    }
}

/**
 * Whether bodies may download ahead on [network]. Data saver holds them back whatever the setting,
 * since the user asked every app for that.
 */
fun BodyPrefetch.allows(network: NetworkCost): Boolean =
    when (this) {
        BodyPrefetch.Always -> !network.dataSaver
        BodyPrefetch.Unmetered -> !network.dataSaver && !network.metered
        BodyPrefetch.Never -> false
    }

/** [MessageBodies.prefetch] where the user's [setting] allows it on [network]; nothing otherwise. */
suspend fun MessageBodies.prefetch(
    messages: List<MailMessage>,
    setting: BodyPrefetch,
    network: NetworkCost,
) {
    if (setting.allows(network)) prefetch(messages)
}
