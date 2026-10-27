package com.otus.mail.core.preferences

/** A map of strings kept on the device, read whole and replaced whole on each write. */
interface StringMapStore {
    fun read(): Map<String, String>

    fun write(values: Map<String, String>)
}
