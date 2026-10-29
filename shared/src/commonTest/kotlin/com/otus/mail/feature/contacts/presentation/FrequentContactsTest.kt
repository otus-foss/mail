package com.otus.mail.feature.contacts.presentation

import com.otus.mail.feature.contacts.domain.ContactStore
import com.otus.mail.feature.contacts.domain.MailTrace
import com.otus.mail.feature.contacts.domain.MailTraceSource
import com.otus.mail.feature.contacts.domain.MailTraces
import com.otus.mail.testing.MemoryMapStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class FrequentContactsTest {
    private var now = Instant.parse("2026-10-25T12:00:00Z")
    private val store = MemoryStore()
    private val source = CountingSource()

    private fun TestScope.frequent() = FrequentContacts(source, store, this) { now }

    @Test
    fun scoresEveryLoginAndKeepsThemOnTheDevice() =
        runTest {
            frequent().refresh()
            advanceUntilIdle()

            val again = frequent()
            assertEquals(10.0, again.scores["jana@example.org"])
            assertTrue(store.saved.getValue(ContactStore.FREQUENT).isNotEmpty())
        }

    @Test
    fun aLoginScannedWithinADayIsNotScannedAgain() =
        runTest {
            frequent().refresh()
            advanceUntilIdle()
            now += 23.hours
            frequent().refresh()
            advanceUntilIdle()

            assertEquals(1, source.scans)
        }

    @Test
    fun aLoginScannedADayAgoIsScannedAgain() =
        runTest {
            frequent().refresh()
            advanceUntilIdle()
            now += 24.hours
            frequent().refresh()
            advanceUntilIdle()

            assertEquals(2, source.scans)
        }

    @Test
    fun savingScoresKeepsTheOtherSavedValues() =
        runTest {
            store.write(mapOf(ContactStore.EDITS to "[]"))

            frequent().refresh()
            advanceUntilIdle()

            assertEquals("[]", store.saved[ContactStore.EDITS])
        }

    private inner class CountingSource : MailTraceSource {
        var scans = 0

        override suspend fun logins(): List<String> = listOf("login")

        override suspend fun traces(loginId: String): MailTraces {
            scans++
            return MailTraces(
                listOf(MailTrace(now, listOf("ada@example.org"), listOf("jana@example.org"))),
                emptyList(),
            )
        }
    }

    private class MemoryStore : MemoryMapStore(), ContactStore
}
