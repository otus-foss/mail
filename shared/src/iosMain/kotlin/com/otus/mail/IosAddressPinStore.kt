package com.otus.mail

import com.otus.mail.feature.inbox.domain.AddressPinStore
import platform.Foundation.NSUserDefaults

/** Pinned addresses, kept as one dictionary keyed by login id. */
class IosAddressPinStore(
    private val preferences: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : AddressPinStore {
    override fun read(): Map<String, String> =
        preferences
            .dictionaryForKey(PINS)
            .orEmpty()
            .mapNotNull { (key, lines) -> if (key is String && lines is String) key to lines else null }
            .toMap()

    override fun write(pins: Map<String, String>) {
        preferences.setObject(pins, forKey = PINS)
    }

    private companion object {
        const val PINS = "otus.mail.address_pins"
    }
}
