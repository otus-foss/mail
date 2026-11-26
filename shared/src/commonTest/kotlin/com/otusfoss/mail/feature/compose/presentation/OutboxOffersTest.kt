package com.otusfoss.mail.feature.compose.presentation

import com.otusfoss.mail.core.messages.domain.SendFailure
import com.otusfoss.mail.core.storage.DeviceStore
import com.otusfoss.mail.feature.compose.domain.MailOutbox
import com.otusfoss.mail.feature.compose.domain.OutboxProblem
import com.otusfoss.mail.feature.compose.domain.OutboxStore
import com.otusfoss.mail.testing.DAY_FIRST
import com.otusfoss.mail.testing.MemoryMapStore
import com.otusfoss.mail.testing.OutgoingMailFixture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

/** A message the outbox could not send while no toast of its own was on screen is still offered. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class OutboxOffersTest : OutgoingMailFixture() {
    private val memory = MemoryMapStore()

    private fun outbox() =
        MailOutbox(DeviceStore(memory, OutboxStore), sender, checker, scheduled, io = EmptyCoroutineContext)

    private fun TestScope.start(outbox: MailOutbox) {
        OutgoingMail(backgroundScope, toasts, scheduled, outbox, { DAY_FIRST }) { hold }.resume { opened += it }
        runCurrent()
    }

    /** The device held it for Undo and the app closed during the window. */
    @Test
    fun aMessageTheServerRefusesAtTheNextStartSaysSo() =
        runTest {
            outbox().add(draft, null, due = NOW)

            failure = SendFailure.Recipients
            start(outbox())

            assertEquals(1, runs.size, "The start tried to send it")
            assertEquals(listOf("Couldn't send"), toasts.messages.map { it.text })
        }

    /** The background job ran the outbox, and the user opens the app in the process that run left alive. */
    @Test
    fun aMessageRefusedInABackgroundRunIsOfferedWhenTheAppOpensInTheSameProcess() =
        runTest {
            val outbox = outbox()
            outbox.add(draft, null, due = NOW)

            failure = SendFailure.Recipients
            outbox.resume()
            assertEquals(1, runs.size, "The background run tried to send it")

            start(outbox)
            assertEquals(listOf(OutboxProblem.Refused), outboxProblems(), "The message still waits for the user")
            assertEquals(listOf("Couldn't send"), toasts.messages.map { it.text })
            assertEquals(1, runs.size, "A refused message does not go again by itself")
        }

    private fun outboxProblems() =
        OutboxStore.ENTRIES
            .of(memory.saved)
            .orEmpty()
            .map { it.problem }
}
