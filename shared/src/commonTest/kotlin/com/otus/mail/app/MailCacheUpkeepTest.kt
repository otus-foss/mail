package com.otus.mail.app

import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.core.cache.CacheKind
import com.otus.mail.core.cache.InMemoryMailCache
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
}
