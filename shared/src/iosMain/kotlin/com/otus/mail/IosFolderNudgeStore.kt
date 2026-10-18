package com.otus.mail

import com.otus.mail.feature.folders.domain.FolderNudgeStore
import platform.Foundation.NSUserDefaults

/** Nudged folder ids, kept as one dictionary keyed by login id. */
class IosFolderNudgeStore(
    private val preferences: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : FolderNudgeStore {
    override fun read(): Map<String, String> =
        preferences
            .dictionaryForKey(NUDGES)
            .orEmpty()
            .mapNotNull { (key, lines) -> if (key is String && lines is String) key to lines else null }
            .toMap()

    override fun write(nudges: Map<String, String>) {
        preferences.setObject(nudges, forKey = NUDGES)
    }

    private companion object {
        const val NUDGES = "otus.mail.folder_nudges"
    }
}
