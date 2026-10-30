package com.otus.mail.core.avatar

/**
 * Where sender avatars get website icons while Website icons is on. [Websites] asks each sender's
 * website, which then sees the device's IP address. [DuckDuckGo] and [Bulwark] ask an icon service
 * instead, so sender websites never see the device. [Bulwark] is the user's own Bulwark webmail
 * server, which also answers brand logos.
 */
enum class IconSource {
    Websites,
    DuckDuckGo,
    Bulwark,
    ;

    val storageValue: String get() = name.lowercase()

    companion object {
        /** The choice saved as [stored], or null when none is. */
        fun decode(stored: String?): IconSource? = entries.firstOrNull { it.storageValue == stored }
    }
}
