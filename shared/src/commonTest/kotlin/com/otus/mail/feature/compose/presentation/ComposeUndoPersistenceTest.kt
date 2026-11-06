package com.otus.mail.feature.compose.presentation

import com.otus.mail.core.mail.MailFailure
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.Draft
import com.otus.mail.core.messages.domain.DraftFailure
import com.otus.mail.core.messages.domain.MailSender
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ComposeUndoPersistenceTest {
    private val saved =
        Draft(
            "a",
            from = MailSender("Me", "me@example.org", "a"),
            to = listOf(MailSender("Ada", "ada@example.org")),
            body = "Saved body",
            emailId = "existing",
        )

    @Test
    fun undoDiscardThenClosePersistsEditsMadeBeforeAutosave() =
        runTest {
            val drafts = FakeDrafts()
            val state = state(drafts)
            state.edit { it.copy(body = "Unsaved body") }
            state.discard()
            val resumed = state(drafts, state.reopening())
            resumed.close()
            runCurrent()

            assertEquals("Unsaved body", drafts.saved.single().body)
            assertEquals("existing", drafts.saved.single().emailId)
            resumed.close()
            runCurrent()
            assertEquals(1, drafts.saved.size)
        }

    @Test
    fun undoSendThenClosePersistsEditsMadeBeforeAutosave() =
        runTest {
            val drafts = FakeDrafts()
            val state = state(drafts)
            state.edit { it.copy(subject = "New subject") }
            assertNotNull(state.handOff())
            state(drafts, state.reopening()).close()
            runCurrent()

            assertEquals("New subject", drafts.saved.single().subject)
            assertEquals("existing", drafts.saved.single().emailId)
        }

    @Test
    fun failedSaveAfterUndoKeepsTheEditsPendingForRetry() =
        runTest {
            val drafts = FakeDrafts().apply { failure = DraftFailure.Mail(MailFailure.ServerProblem) }
            val state = state(drafts)
            state.edit { it.copy(body = "Unsaved body") }
            state.discard()
            val resumed = state(drafts, state.reopening())
            resumed.close()
            runCurrent()
            assertNotNull(resumed.notice)
            assertTrue(drafts.saved.isEmpty())

            drafts.failure = null
            resumed.close()
            runCurrent()
            assertNull(resumed.notice)
            assertEquals("Unsaved body", drafts.saved.single().body)
            assertEquals("existing", drafts.saved.single().emailId)
        }

    private fun TestScope.state(
        drafts: FakeDrafts,
        request: ComposeRequest = ComposeRequest.Resume(saved, ComposeRequest.New("a")),
    ) = ComposeState(
        ComposeServices(drafts, { _, _ -> emptyList() }),
        backgroundScope,
        request,
        ComposeOrigin("a", listOf(ComposeAccount("a", "A")), emptyList()),
    )
}
