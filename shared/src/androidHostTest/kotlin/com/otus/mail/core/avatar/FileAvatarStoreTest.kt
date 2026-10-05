package com.otus.mail.core.avatar

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FileAvatarStoreTest {
    private val contact = AvatarCacheKey("private-login", "alice@company.example")
    private val domain = AvatarCacheKey(null, "company.example")

    @Test
    fun recreatedStoreRestoresImagesAndMissesAndPurgesOnlyPrivateAccountRecords() =
        runTest {
            val directory = Files.createTempDirectory("otus-avatars").toFile()
            try {
                val store = FileAvatarStore(directory.path)
                val bytes = byteArrayOf(1, 2, 3)
                store.write(contact, StoredAvatar(AvatarImage(bytes), 42))
                store.write(domain, StoredAvatar(AvatarImage(bytes), 43))
                val missing = contact.copy(value = "missing")
                store.write(missing, StoredAvatar(null, 44))
                val restored = FileAvatarStore(directory.path)
                assertContentEquals(bytes, restored.read(contact)?.image?.bytes)
                assertEquals(44, restored.read(missing)?.created)
                assertFalse(directory.listFiles().orEmpty().any { "alice" in it.name || "private-login" in it.name })
                restored.removeAccount("private-login")
                assertNull(restored.read(contact))
                assertNull(restored.read(missing))
                assertContentEquals(bytes, restored.read(domain)?.image?.bytes)
                restored.clear()
                assertEquals(0, directory.listFiles().orEmpty().size)
            } finally {
                directory.deleteRecursively()
            }
        }

    @Test
    fun boundsDiskUsageAndRejectsCorruptAndOversizedRecords() =
        runTest {
            val directory = Files.createTempDirectory("otus-avatars").toFile()
            try {
                val store = FileAvatarStore(directory.path, maxBytes = 25, maxEntries = 2)
                store.write(contact, StoredAvatar(AvatarImage(byteArrayOf(1, 2, 3)), 1))
                File(directory, "orphan.avatar.part").writeBytes(ByteArray(30))
                store.write(domain, StoredAvatar(AvatarImage(byteArrayOf(4, 5, 6)), 2))
                assertFalse(File(directory, "orphan.avatar.part").exists())
                store.write(contact.copy(value = "new"), StoredAvatar(AvatarImage(byteArrayOf(7, 8, 9)), 3))
                assertNull(store.read(contact))
                assertEquals(2, directory.listFiles().orEmpty().size)
                val public = directory.listFiles()!!.first { it.name.startsWith("public-") }
                public.writeBytes(byteArrayOf(1))
                assertNull(store.read(domain))
                store.write(domain, StoredAvatar(AvatarImage(ByteArray(MAX_AVATAR_BYTES.toInt() + 1)), 4))
                assertNull(store.read(domain))
            } finally {
                directory.deleteRecursively()
            }
        }
}
