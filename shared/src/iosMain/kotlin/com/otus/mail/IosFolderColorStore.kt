package com.otus.mail

import com.otus.mail.feature.folders.domain.FolderColorStore
import platform.Foundation.NSUserDefaults

/** Folder color names, kept as one dictionary keyed by each folder's key. */
class IosFolderColorStore(
    private val preferences: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : FolderColorStore {
    override fun read(): Map<String, String> =
        preferences
            .dictionaryForKey(COLORS)
            .orEmpty()
            .mapNotNull { (key, name) -> if (key is String && name is String) key to name else null }
            .toMap()

    override fun write(names: Map<String, String>) {
        preferences.setObject(names, forKey = COLORS)
    }

    private companion object {
        const val COLORS = "otus.mail.folder_colors"
    }
}
