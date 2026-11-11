package com.otusfoss.mail.core.storage

import com.otusfoss.mail.testing.MemoryMapStore
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FileMapStoreTest {
    private val folder =
        Path(SystemTemporaryDirectory, "file-map-store-${Random.nextLong().toULong()}")
            .also { SystemFileSystem.createDirectories(it) }
    private val file = Path(folder, "outbox.json")

    @AfterTest
    fun removeFolder() {
        SystemFileSystem.list(folder).forEach { SystemFileSystem.delete(it) }
        SystemFileSystem.delete(folder)
    }

    @Test
    fun movesSavedValuesOutOfTheOldStoreOnce() {
        val old = MemoryMapStore(mapOf("draft" to "Hello"))

        assertEquals(mapOf("draft" to "Hello"), FileMapStore(file, old).read())
        assertEquals(emptyMap(), old.saved, "The old store keeps nothing")
        assertEquals(mapOf("draft" to "Hello"), FileMapStore(file, MemoryMapStore()).read())
    }

    @Test
    fun aWriteReplacesTheFileAndEmptiesTheOldStore() {
        val old = MemoryMapStore(mapOf("draft" to "Old"))
        val store = FileMapStore(file, old)

        store.write(mapOf("draft" to "New", "other" to "Two"))
        store.write(mapOf("other" to "Two"))

        assertEquals(mapOf("other" to "Two"), store.read())
        assertEquals(emptyMap(), old.saved)
        assertEquals(listOf(file), SystemFileSystem.list(folder).toList(), "No partial file remains")
    }

    @Test
    fun anUnreadableFileReadsAsEmpty() {
        SystemFileSystem.sink(file).close()

        assertEquals(emptyMap(), FileMapStore(file, MemoryMapStore(mapOf("draft" to "Old"))).read())
    }
}
