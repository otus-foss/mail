package com.otusfoss.mail.feature.compose.presentation

import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.testing.FakeDrafts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/** Undo right after Send or Discard, while an autosave still runs, opens the copy that save stores. */
@OptIn(ExperimentalCoroutinesApi::class)
class ComposeUndoNewestCopyTest {
    private val answer = CompletableDeferred<Unit>()
    private val drafts = FakeDrafts(answer = answer)

    private val saved =
        Draft(
            "a",
            from = MailSender("Me", "me@example.org", "a"),
            to = listOf(MailSender("Ada", "ada@example.org")),
            body = "Saved body",
            emailId = "existing",
        )

    @Test
    fun undoAfterSendOpensTheCopyASlowSaveStored() =
        runTest {
            val state = savingSlowly()
            val outgoing = assertNotNull(state.handOff())
            val message = Outgoing(outgoing, state.reopening(), open = {})
            val reopened = async { message.reopenSaved() }
            runCurrent()
            assertFalse(reopened.isCompleted, "Undo waits for the save")

            answer.complete(Unit)
            assertEquals("d1", reopened.await().draft.emailId)
        }

    @Test
    fun undoAfterDiscardOpensTheCopyASlowSaveStored() =
        runTest {
            val state = savingSlowly()
            state.discard()
            val reopened = async { state.reopeningSaved() }
            runCurrent()
            assertFalse(reopened.isCompleted, "Undo waits for the save")

            answer.complete(Unit)
            assertEquals("d1", reopened.await().draft.emailId)
        }

    /** A draft with an edit whose save has stored the copy but not yet answered. */
    private fun TestScope.savingSlowly(): ComposeState {
        val state =
            ComposeState(
                ComposeServices(drafts, { _, _ -> emptyList() }),
                backgroundScope,
                ComposeRequest.Resume(saved, ComposeRequest.New("a")),
                ComposeOrigin("a", listOf(ComposeAccount("a", "A")), emptyList()),
            )
        state.edit { it.copy(subject = "New subject") }
        state.close()
        runCurrent()
        assertEquals(1, drafts.saved.size, "The save is under way")
        return state
    }
}
