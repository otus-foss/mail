package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.DraftException
import com.otus.mail.feature.inbox.domain.DraftFailure
import com.otus.mail.feature.inbox.domain.DraftRepository
import com.otus.mail.feature.inbox.domain.DraftSender
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.SendIdentity
import com.otus.mail.feature.inbox.domain.SentMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ComposeMigrationTest {
    private val work = SendIdentity("work", MailSender("Me", "work@example.org", "a"))
    private val home = SendIdentity("home", MailSender("Me", "home@example.org", "b"))

    @Test
    fun aFailedDestinationSaveKeepsTheSourceUntilRetrySucceeds() =
        runTest {
            val drafts = Copies()
            val state = state(drafts)
            advanceTimeBy(DELAY + 1)
            drafts.fail = "b"
            state.chooseFrom(home)
            advanceTimeBy(DELAY + 1)
            assertEquals(
                setOf("a"),
                drafts.copies.values
                    .map { it.accountId }
                    .toSet(),
            )
            assertTrue(drafts.removed.isEmpty())
            assertNotNull(state.notice)

            drafts.fail = null
            state.close()
            runCurrent()
            assertEquals(
                setOf("b"),
                drafts.copies.values
                    .map { it.accountId }
                    .toSet(),
            )
            assertEquals(listOf("a" to "1"), drafts.removed)
        }

    @Test
    fun anInFlightSourceSaveIsRetainedWhenTheDestinationFails() =
        runTest {
            val drafts = Copies()
            val answer = CompletableDeferred<Unit>()
            drafts.waitFor = "a" to answer
            val state = state(drafts)
            advanceTimeBy(DELAY + 1)
            state.chooseFrom(home)
            drafts.fail = "b"
            answer.complete(Unit)
            advanceTimeBy(DELAY + 1)
            assertEquals(setOf("a" to "1"), drafts.copies.keys)
            assertTrue(drafts.removed.isEmpty())
        }

    @Test
    fun switchingBackBeforeTheDestinationSaveKeepsTheOriginalCopy() =
        runTest {
            val drafts = Copies()
            val state = state(drafts)
            advanceTimeBy(DELAY + 1)
            state.chooseFrom(home)
            state.chooseFrom(work)
            advanceTimeBy(DELAY + 1)
            assertEquals("1", state.draft.emailId)
            assertEquals(1, drafts.writes.size)
            assertTrue(drafts.removed.isEmpty())
        }

    @Test
    fun switchingBackDuringADestinationSaveDoesNotReuseTheDeletedSourceId() =
        runTest {
            val drafts = Copies()
            val state = state(drafts)
            advanceTimeBy(DELAY + 1)
            val answer = CompletableDeferred<Unit>()
            drafts.waitFor = "b" to answer
            state.chooseFrom(home)
            advanceTimeBy(DELAY + 1)
            state.chooseFrom(work)
            answer.complete(Unit)
            runCurrent()
            drafts.fail = "a"
            advanceTimeBy(DELAY + 1)
            assertEquals(setOf("b" to "2"), drafts.copies.keys)
            assertNull(drafts.writes.last().emailId)
            assertEquals(listOf("a" to "1"), drafts.removed)
        }

    @Test
    fun sendingFromTheDestinationKeepsTheSourceUntilSubmissionSucceeds() =
        runTest {
            val drafts = Copies()
            var accepted = false
            val sender =
                DraftSender { draft, _, _ ->
                    assertEquals("b", draft.accountId)
                    assertNull(draft.emailId)
                    assertTrue(drafts.removed.isEmpty())
                    if (accepted) {
                        Result.success(SentMessage("b", "sent", "submission"))
                    } else {
                        Result.failure(IllegalStateException("Offline"))
                    }
                }
            val state = state(drafts, sender)
            advanceTimeBy(DELAY + 1)
            state.chooseFrom(home)
            val send = assertNotNull(state.handOff())
            assertTrue(send(null).isFailure)
            assertEquals(setOf("a" to "1"), drafts.copies.keys)
            accepted = true
            assertTrue(send(null).isSuccess)
            assertTrue(drafts.copies.isEmpty())
            assertEquals(listOf("a" to "1"), drafts.removed)
        }

    @Test
    fun discardingBeforeTheDestinationSaveDeletesTheRetainedSource() =
        runTest {
            val drafts = Copies()
            val state = state(drafts)
            advanceTimeBy(DELAY + 1)
            state.chooseFrom(home)
            val delete = state.discard()
            advanceTimeBy(DELAY + 1)
            assertTrue(drafts.removed.isEmpty())
            delete()
            runCurrent()
            assertTrue(drafts.copies.isEmpty())
            assertEquals(listOf("a" to "1"), drafts.removed)
        }

    private fun TestScope.state(
        drafts: DraftRepository,
        sender: DraftSender = DraftSender { _, _, _ -> error("No sending here") },
    ): ComposeState =
        ComposeState(
            ComposeServices(drafts, sender, { _, _ -> emptyList() }),
            backgroundScope,
            ComposeRequest.New("a"),
            ComposeOrigin("a", listOf(ComposeAccount("a", "Work"), ComposeAccount("b", "Home")), emptyList()),
            saveDelayMillis = DELAY,
        ).apply {
            chooseFrom(work)
            edit { it.copy(subject = "Keep this", to = listOf(MailSender("Ada", "ada@example.org"))) }
        }

    private class Copies : DraftRepository {
        val copies = mutableMapOf<Pair<String, String>, Draft>()
        val writes = mutableListOf<Draft>()
        val removed = mutableListOf<Pair<String, String>>()
        var fail: String? = null
        var waitFor: Pair<String, CompletableDeferred<Unit>>? = null

        override suspend fun identities(accountId: String): List<SendIdentity> = emptyList()

        override suspend fun save(draft: Draft): Result<String> {
            writes += draft
            if (draft.accountId == fail) return Result.failure(DraftException(DraftFailure.ServerProblem))
            val id = writes.size.toString()
            copies[draft.accountId to id] = draft
            waitFor?.takeIf { it.first == draft.accountId }?.second?.await()
            draft.emailId?.let { copies.remove(draft.accountId to it) }
            return Result.success(id)
        }

        override suspend fun discard(
            accountId: String,
            emailId: String,
        ): Result<Unit> {
            removed += accountId to emailId
            copies.remove(accountId to emailId)
            return Result.success(Unit)
        }
    }

    private companion object {
        const val DELAY = 1_000L
    }
}
