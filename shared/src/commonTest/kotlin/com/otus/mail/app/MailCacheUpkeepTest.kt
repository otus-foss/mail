package com.otus.mail.app

import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.core.cache.CacheKind
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.files.InMemoryDownloadFolder
import com.otus.mail.core.files.StoredKey
import com.otus.mail.core.files.storedName
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class MailCacheUpkeepTest {
    @Test
    fun removingAnAccountForgetsOnlyItsCachedMail() =
        runTest {
            val logins = InMemoryLoginStore()
            logins.save(JmapLogin("ada", "https://mail.example.org/jmap/session", "ada@example.org", "Ada"), "secret")
            logins.save(JmapLogin("bob", "https://mail.example.com/jmap/session", "bob@example.com", "Bob"), "secret")
            val cache = InMemoryMailCache()
            listOf("ada", "bob").forEach { login -> cache.write(login) { put(CacheKind.Email, "e1", login) } }

            MailServices(logins, stores = MailDeviceStores(cache = cache)).accounts.remove("ada")

            assertEquals(emptyMap(), cache.readAll("ada", CacheKind.Email))
            assertEquals(mapOf("e1" to "bob"), cache.readAll("bob", CacheKind.Email))
        }

    @Test
    fun trimmingKeepsBodiesOfTheLastNinetyDays() =
        runTest {
            val now = Instant.parse("2026-10-18T12:00:00Z")
            val cache = InMemoryMailCache()
            cache.write("ada") {
                put(CacheKind.Body, "old", "old", (now - 91.days).toEpochMilliseconds())
                put(CacheKind.Body, "recent", "recent", (now - 89.days).toEpochMilliseconds())
                put(CacheKind.Email, "old", "old", (now - 91.days).toEpochMilliseconds())
            }

            MailServices(stores = MailDeviceStores(cache = cache)).trimCache(now)

            assertEquals(setOf("recent"), cache.readAll("ada", CacheKind.Body).keys)
            assertEquals(setOf("old"), cache.readAll("ada", CacheKind.Email).keys)
        }

    @Test
    fun removingAnAccountDeletesItsDownloadedFilesEvenKeptOnes() =
        runTest {
            val logins = InMemoryLoginStore()
            logins.save(JmapLogin("ada", "https://mail.example.org/jmap/session", "ada@example.org", "Ada"), "secret")
            val downloads = InMemoryDownloadFolder()
            listOf("ada", "bob").forEach { login ->
                val key = StoredKey(storedName(login), "file")
                downloads.write(key, "report.pdf") { append -> append(byteArrayOf(1)) }
                downloads.setKept(key, kept = true)
            }

            MailServices(logins, stores = MailDeviceStores(downloads = downloads)).accounts.remove("ada")

            assertEquals(listOf(storedName("bob")), downloads.entries().map { it.key.group })
        }

    @Test
    fun trimmingDropsDownloadsUnusedForAWeekUnlessKept() =
        runTest {
            val now = Instant.parse("2026-10-18T12:00:00Z")
            var clock = (now - 8.days).toEpochMilliseconds()
            val downloads = InMemoryDownloadFolder { clock }
            val old = StoredKey(storedName("ada"), "old")
            val keptFile = StoredKey(storedName("ada"), "kept")
            for (key in listOf(old, keptFile)) downloads.write(key, "file.pdf") { append -> append(byteArrayOf(1)) }
            downloads.setKept(keptFile, kept = true)
            clock = (now - 6.days).toEpochMilliseconds()
            downloads.write(StoredKey(storedName("ada"), "recent"), "file.pdf") { append -> append(byteArrayOf(1)) }

            MailServices(stores = MailDeviceStores(downloads = downloads)).trimCache(now)

            assertEquals(setOf("kept", "recent"), downloads.entries().map { it.key.id }.toSet())
        }
}
