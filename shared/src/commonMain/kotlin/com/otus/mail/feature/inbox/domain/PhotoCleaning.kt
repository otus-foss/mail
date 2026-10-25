package com.otus.mail.feature.inbox.domain

/**
 * Whether attached photos lose their metadata and file name: [Ask] each time, with removing them
 * offered first, [Always], or [Never].
 */
enum class PhotoCleaning {
    Ask,
    Always,
    Never,
    ;

    val storageValue: String get() = name.lowercase()

    companion object {
        /** The choice saved as [stored], else [Ask]. */
        fun decode(stored: String?): PhotoCleaning = entries.firstOrNull { it.storageValue == stored } ?: Ask
    }
}
