package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.DraftException
import com.otus.mail.feature.inbox.domain.DraftFailure
import com.otus.mail.feature.inbox.domain.DraftRepository
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.OriginalMessage
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
    private class FakeDrafts(
        val identities: List<MailSender> = emptyList(),
    ) : DraftRepository {
        val saved = mutableListOf<Draft>()
        val discarded = mutableListOf<String>()
        var failure: DraftFailure? = null

        override suspend fun identities(accountId: String) = identities

        override suspend fun save(draft: Draft): Result<String> {
            failure?.let { return Result.failure(DraftException(it)) }
            saved += draft
            return Result.success("d${saved.size}")
        }

        override suspend fun discard(
            accountId: String,
            emailId: String,
        ): Result<Unit> {
            discarded += emailId
            return Result.success(Unit)
        }
    }

    private fun TestScope.state(
        drafts: DraftRepository,
        request: ComposeRequest = ComposeRequest.New(),
    ) = ComposeState(drafts, backgroundScope, request, "a1", listOf("me@example.com"), saveDelayMillis = DELAY)

    @Test
    fun editsSaveOnceTheUserPausesAndEachSaveReplacesTheLastCopy() =
        runTest {
            val drafts = FakeDrafts()
            val state = state(drafts)
            state.editRecipients(RecipientField.To, "Ada <ada@example.com>, ")
            advanceTimeBy(DELAY / 2)
            state.edit { it.copy(subject = "Lunch") }
            advanceTimeBy(DELAY / 2 + 1)
            assertEquals(0, drafts.saved.size)
            advanceTimeBy(DELAY)
            assertEquals(listOf(MailSender("Ada", "ada@example.com")), drafts.saved.single().to)
            assertEquals("d1", state.draft.emailId)
            assertEquals("Ada <ada@example.com>, ", state.recipientText.getValue(RecipientField.To))

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
    fun discardingDeletesTheSavedCopyAndStopsSaving() =
        runTest {
            val drafts = FakeDrafts()
            val state = state(drafts)
            state.edit { it.copy(subject = "Hi") }
            advanceTimeBy(DELAY + 1)
            state.edit { it.copy(subject = "Hi there") }
            state.discard()
            state.close()
            advanceTimeBy(DELAY * 2)
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
    fun aReplyIsSentFromTheIdentityTheOriginalWentTo() =
        runTest {
            val work = MailSender("Me", "work@example.com")
            val me = MailSender("Me", "me@example.com")
            val state = state(FakeDrafts(listOf(work, me)), ComposeRequest.Reply(original, all = false))
            state.loadIdentities()
            runCurrent()
            assertEquals(me, state.draft.from)
            assertEquals(listOf(work, me), state.identities)
            assertFalse(state.edited)
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
