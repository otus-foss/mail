package com.otus.mail

import com.otus.mail.feature.folders.domain.ReadLaterStore
import platform.Foundation.NSUserDefaults

/** Each login's Read later folder id, kept as one dictionary keyed by login id. */
class IosReadLaterStore(
    private val preferences: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : ReadLaterStore {
    override fun read(): Map<String, String> =
        preferences
            .dictionaryForKey(FOLDERS)
            .orEmpty()
            .mapNotNull { (login, folder) -> if (login is String && folder is String) login to folder else null }
            .toMap()

    override fun write(folders: Map<String, String>) {
        preferences.setObject(folders, forKey = FOLDERS)
    }

    private companion object {
        const val FOLDERS = "otus.mail.read_later_folders"
    }
}
