package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.DraftFailure
import com.otus.mail.feature.inbox.domain.DraftRepository
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.OriginalMessage
import com.otus.mail.feature.inbox.domain.SendIdentity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ComposeStateTest {
    private fun TestScope.state(
        drafts: DraftRepository,
        request: ComposeRequest = ComposeRequest.New(),
    ) = ComposeState(
        ComposeServices(drafts, { _, _ -> error("No sending here") }, { _, _ -> emptyList() }),
        backgroundScope,
        request,
        ComposeOrigin(
            "a1",
            listOf(ComposeAccount("a1", "Work"), ComposeAccount("a2", "Home")),
            listOf("me@example.com"),
        ),
        saveDelayMillis = DELAY,
    )

    @Test
    fun editsSaveOnceTheUserPausesAndEachSaveReplacesTheLastCopy() =
        runTest {
            val drafts = FakeDrafts()
            val state = state(drafts)
            state.recipients.type(RecipientField.To, "Ada <ada@example.com>,")
            advanceTimeBy(DELAY / 2)
            state.edit { it.copy(subject = "Lunch") }
            advanceTimeBy(DELAY / 2 + 1)
            assertEquals(0, drafts.saved.size)
            advanceTimeBy(DELAY)
            assertEquals(listOf(MailSender("Ada", "ada@example.com")), drafts.saved.single().to)
            assertEquals("d1", state.draft.emailId)
            assertEquals("", state.recipients.typed.getValue(RecipientField.To))

            state.edit { it.copy(body = "Noon?") }
            state.close()
            runCurrent()
            assertEquals("d1", drafts.saved.last().emailId)
            assertEquals("d2", state.draft.emailId)
        }

    @Test
    fun leavingAnUntouchedDraftSavesNothing() =
        runTest {
            val drafts = FakeDrafts()
            val reply = ComposeRequest.Reply(original, all = false)
            val state = state(drafts, reply)
            assertFalse(state.edited)
            state.close()
            runCurrent()
            assertTrue(drafts.saved.isEmpty())
        }

    @Test
    fun discardingStopsSavingAndDeletesTheCopyOnlyOnceUndoHasPassed() =
        runTest {
            val drafts = FakeDrafts()
            val state = state(drafts)
            state.edit { it.copy(subject = "Hi") }
            advanceTimeBy(DELAY + 1)
            state.edit { it.copy(subject = "Hi there") }
            state.discard()
            state.close()
            advanceTimeBy(DELAY * 2)
            assertTrue(drafts.discarded.isEmpty())
            state.deleteDiscarded()
            runCurrent()
            assertEquals(listOf("d1"), drafts.discarded)
            assertEquals(1, drafts.saved.size)
        }

    @Test
    fun aFailedSaveShowsANoticeUntilTheNextSaveWorks() =
        runTest {
            val drafts = FakeDrafts().apply { failure = DraftFailure.NoDraftsFolder }
            val state = state(drafts)
            state.edit { it.copy(body = "Hello") }
            advanceTimeBy(DELAY + 1)
            assertEquals("This account has no Drafts folder, so the draft isn't saved.", state.notice)
            drafts.failure = null
            state.edit { it.copy(body = "Hello!") }
            advanceTimeBy(DELAY + 1)
            assertNull(state.notice)
        }

    @Test
    fun aFailedSaveKeepsTheStoredCopyForTheNextSaveToReplace() =
        runTest {
            val drafts = FakeDrafts()
            val state = state(drafts)
            state.edit { it.copy(body = "Hello") }
            advanceTimeBy(DELAY + 1)
            drafts.failure = DraftFailure.ServerProblem
            state.edit { it.copy(body = "Hello!") }
            advanceTimeBy(DELAY + 1)
            assertEquals("d1", state.draft.emailId)
            drafts.failure = null
            state.close()
            runCurrent()
            assertEquals("d1", drafts.saved.last().emailId)
            assertEquals("d2", state.draft.emailId)
        }

    @Test
    fun aChangeDuringASaveLetsItFinishSoTheNextSaveReplacesItsCopy() =
        runTest {
            val answer = CompletableDeferred<Unit>()
            val drafts = FakeDrafts(answer = answer)
            val state = state(drafts)
            state.edit { it.copy(body = "Hello") }
            advanceTimeBy(DELAY + 1)
            state.edit { it.copy(body = "Hello!") }
            answer.complete(Unit)
            runCurrent()
            assertEquals("d1", state.draft.emailId)
            assertEquals("Hello!", state.draft.body)
            advanceTimeBy(DELAY + 1)
            assertEquals("d1", drafts.saved.last().emailId)
            assertEquals("d2", state.draft.emailId)
        }

    @Test
    fun aDiscardDuringASaveDeletesTheCopyThatSaveStored() =
        runTest {
            val answer = CompletableDeferred<Unit>()
            val drafts = FakeDrafts(answer = answer)
            val state = state(drafts)
            state.edit { it.copy(subject = "Hi") }
            advanceTimeBy(DELAY + 1)
            state.discard()
            state.deleteDiscarded()
            answer.complete(Unit)
            runCurrent()
            assertEquals(listOf("d1"), drafts.discarded)
            assertEquals(1, drafts.saved.size)
        }

    @Test
    fun aReplyIsSentFromTheIdentityTheOriginalWentTo() =
        runTest {
            val work = MailSender("Me", "work@example.com")
            val me = MailSender("Me", "me@example.com")
            val state = state(FakeDrafts(listOf(work, me)), ComposeRequest.Reply(original, all = false))
            state.loadIdentities()
            runCurrent()
            assertEquals(me, state.draft.from)
            assertEquals(listOf(work, me, work, me), state.identities.map { it.sender })
            assertFalse(state.edited)
        }

    @Test
    fun aResumedDraftCountsAsWrittenSoItSaves() =
        runTest {
            val drafts = FakeDrafts()
            val resumed = Draft("a1", subject = "Hi", body = "Back again")
            val state = state(drafts, ComposeRequest.Resume(resumed, ComposeRequest.New()))
            assertTrue(state.edited)
            state.close()
            runCurrent()
            assertEquals("Back again", drafts.saved.single().body)
        }

    @Test
    fun sendingFromAnotherAccountMovesTheDraftThere() =
        runTest {
            val drafts = FakeDrafts()
            val state = state(drafts)
            state.edit { it.copy(subject = "Hi") }
            advanceTimeBy(DELAY + 1)
            state.chooseFrom(SendIdentity("i2", MailSender("Me", "me@home.example", "a2")))
            runCurrent()
            assertEquals(listOf("d1"), drafts.discarded)
            advanceTimeBy(DELAY + 1)
            assertEquals("a2", drafts.saved.last().accountId)
            assertNull(drafts.saved.last().emailId)
        }

    private val original =
        OriginalMessage(
            accountId = "a1",
            emailId = "m1",
            subject = "Lunch",
            from = MailSender("Ada", "ada@example.com"),
            to = listOf(MailSender("Me", "me@example.com")),
            cc = emptyList(),
            sentLabel = "2026-10-11 12:30",
            body = "Noon?",
        )

    private companion object {
        const val DELAY = 1_000L
    }
}
