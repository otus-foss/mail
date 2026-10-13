package com.otus.mail.feature.folders.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LatestLoadTest {
    @Test
    fun aNewLoadCancelsTheRunningOne() =
        runTest {
            val loads = LatestLoad(this)
            val never = CompletableDeferred<Unit>()
            var finished = false
            loads.start {
                never.await()
                finished = true
            }
            runCurrent()
            loads.start {}
            runCurrent()
            never.complete(Unit)
            runCurrent()
            assertFalse(finished)
        }

    @Test
    fun aReplacedLoadThatStillFinishesIsNotTheNewest() =
        runTest {
            val loads = LatestLoad(this)
            val answer = CompletableDeferred<Unit>()
            val finished = mutableListOf<Pair<String, Boolean>>()
            loads.start { isLatest ->
                // Like a request already sent, this load finishes although it was cancelled.
                withContext(NonCancellable) {
                    answer.await()
                    finished += "first" to isLatest()
                }
            }
            runCurrent()
            loads.start { isLatest -> finished += "second" to isLatest() }
            runCurrent()
            answer.complete(Unit)
            runCurrent()
            assertEquals(listOf("second" to true, "first" to false), finished)
        }

    @Test
    fun waitingFollowsTheLoadThatReplacesTheAwaitedOne() =
        runTest {
            val loads = LatestLoad(this)
            val first = CompletableDeferred<Unit>()
            val second = CompletableDeferred<Unit>()
            loads.start { withContext(NonCancellable) { first.await() } }
            var waited = false
            launch {
                loads.awaitLatest()
                waited = true
            }
            runCurrent()
            loads.start { second.await() }
            first.complete(Unit)
            runCurrent()
            assertFalse(waited, "The replaced load finished, but the newest one still runs")
            second.complete(Unit)
            runCurrent()
            assertTrue(waited)
        }
}
