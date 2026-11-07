package com.otus.mail.feature.inbox.presentation

import com.otus.mail.core.messages.domain.FoundPerson
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.PeopleField
import com.otus.mail.core.text.TYPING_PAUSE_MILLIS
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FilterPeopleSearchTest {
    private val grace = FoundPerson(MailSender("Grace", "grace@x.org"), 3)

    @Test
    fun aFailedSearchStopsSearching() =
        runTest {
            val failures = mutableListOf<Throwable>()
            val scope =
                CoroutineScope(
                    StandardTestDispatcher(testScheduler) + SupervisorJob() +
                        CoroutineExceptionHandler { _, error -> failures += error },
                )
            val search = FilterPeopleSearch(PeopleField.From, scope) { _, _ -> error("The server went away") }
            search.search("grace")
            assertTrue(search.searching)
            advanceTimeBy(TYPING_PAUSE_MILLIS + 1)
            runCurrent()
            assertFalse(search.searching)
            assertEquals(1, failures.size, "The failure still reaches the scope")
        }

    @Test
    fun aCancelledSearchLeavesTheNewerOneSearching() =
        runTest {
            val answer = CompletableDeferred<List<FoundPerson>>()
            val search = FilterPeopleSearch(PeopleField.From, backgroundScope) { _, _ -> answer.await() }
            search.search("gr")
            advanceTimeBy(TYPING_PAUSE_MILLIS + 1)
            runCurrent()
            search.search("grace")
            runCurrent()
            assertTrue(search.searching, "Cancelling the first search does not end the second")
            advanceTimeBy(TYPING_PAUSE_MILLIS + 1)
            answer.complete(listOf(grace))
            runCurrent()
            assertFalse(search.searching)
            assertEquals(listOf(grace), search.found)
        }

    @Test
    fun aFailedSearchDropsTheLastResults() =
        runTest {
            var calls = 0
            val scope =
                CoroutineScope(
                    StandardTestDispatcher(testScheduler) + SupervisorJob() + CoroutineExceptionHandler { _, _ -> },
                )
            val search =
                FilterPeopleSearch(PeopleField.From, scope) { _, _ ->
                    if (calls++ == 0) listOf(grace) else error("The server went away")
                }
            search.search("gr")
            advanceTimeBy(TYPING_PAUSE_MILLIS + 1)
            runCurrent()
            assertEquals(listOf(grace), search.found)
            search.search("grace")
            advanceTimeBy(TYPING_PAUSE_MILLIS + 1)
            runCurrent()
            assertEquals(emptyList(), search.found, "Results for other text do not stay on screen")
        }

    @Test
    fun typingSuggestsUntilTheLineIsLeftOrCleared() {
        val search = FilterPeopleSearch(PeopleField.From)
        search.search("gr")
        assertTrue(search.suggesting)
        search.dismiss()
        assertFalse(search.suggesting, "Leaving the line hides the matches")
        assertEquals("gr", search.text, "and keeps the text to fix")
        search.search("gr")
        assertTrue(search.suggesting, "Typing again shows them")
        search.clear()
        assertFalse(search.suggesting)
    }
}
