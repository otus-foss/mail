package com.otus.mail.testing

import com.otus.storage.StringMapStore

/** A [StringMapStore] in memory: a test's store extends it with the store type the code under test takes. */
open class MemoryMapStore(
    var saved: Map<String, String> = emptyMap(),
) : StringMapStore {
    override fun read() = saved

    override fun write(values: Map<String, String>) {
        saved = values
    }
}
