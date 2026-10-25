package com.otus.mail

import com.otus.mail.feature.contacts.domain.ContactStore
import platform.Foundation.NSUserDefaults

/** Contact changes waiting for the server, and the device's own contact choices, as one dictionary. */
class IosContactStore(
    private val preferences: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : ContactStore {
    override fun read(): Map<String, String> =
        preferences
            .dictionaryForKey(CONTACTS)
            .orEmpty()
            .mapNotNull { (key, value) -> if (key is String && value is String) key to value else null }
            .toMap()

    override fun write(values: Map<String, String>) {
        preferences.setObject(values, forKey = CONTACTS)
    }

    private companion object {
        const val CONTACTS = "otus.mail.contacts"
    }
}
