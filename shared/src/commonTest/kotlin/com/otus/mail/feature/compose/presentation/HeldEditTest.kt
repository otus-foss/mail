package com.otus.mail.feature.compose.presentation

import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.ScheduledSends
import com.otus.mail.core.messages.domain.SentMessage
import com.otus.mail.testing.FakeDrafts
import com.otus.mail.testing.OutgoingMailFixture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A draft that edits a scheduled send leaves that send alone until Send replaces it. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class HeldEditTest : OutgoingMailFixture() {
    private val held = SentMessage("a1", "d1", "s1")
    private val editing = ComposeRequest.Resume(draft, ComposeRequest.New("a1"), held = held)

    private fun TestScope.state(drafts: FakeDrafts) =
        ComposeState(
            ComposeServices(drafts, { _, _ -> emptyList() }),
            backgroundScope,
            editing,
            ComposeOrigin("a1", listOf(ComposeAccount("a1", "Work")), listOf("me@example.com")),
            saveDelayMillis = DELAY,
        )

    @Test
    fun editingAScheduledSendSavesNothingSoLeavingKeepsItAsItWas() =
        runTest {
            val drafts = FakeDrafts()
            val state = state(drafts)
            state.edit { it.copy(subject = "Dinner") }
            advanceTimeBy(DELAY + 1)
            state.close()
            runCurrent()
            assertTrue(drafts.saved.isEmpty(), "A save would replace the copy the server still sends")
            assertTrue(drafts.discarded.isEmpty())
        }

    @Test
    fun undoAfterDiscardStillEditsTheScheduledSend() =
        runTest {
            val state = state(FakeDrafts())
            state.edit { it.copy(subject = "Dinner") }
            state.discard()
            assertEquals(held, state.reopening().held)
            assertEquals("Dinner", state.reopening().draft.subject)
        }

    @Test
    fun sendCancelsTheScheduledSendAndThenForgetsIt() =
        runTest {
            val free = assertNotNull(outgoing(editing).released(scheduled))
            assertEquals(listOf(held), cancelled)
            assertNull(free.reopen.held, "Undo opens an ordinary draft: the send is gone")
            assertEquals("d1", free.reopen.draft.emailId)
        }

    @Test
    fun aSendThatCannotBeCanceledIsNotReplaced() =
        runTest {
            val refusing =
                object : ScheduledSends by scheduled {
                    override suspend fun cancel(message: SentMessage) = Result.failure<Unit>(IllegalStateException())
                }
            assertNull(outgoing(editing).released(refusing))
        }

    @Test
    fun anOrdinaryDraftGoesAsItIs() =
        runTest {
            assertSame(message, message.released(scheduled))
            assertTrue(cancelled.isEmpty())
        }

    private fun outgoing(reopen: ComposeRequest.Resume) =
        Outgoing(OutgoingDraft(ready = { draft }, answered = null, sent = {}), reopen, open = { opened += it })

    private companion object {
        const val DELAY = 1_000L
    }
}
