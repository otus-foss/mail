package com.otusfoss.mail.feature.compose.presentation

import com.otusfoss.mail.core.messages.domain.AnsweredMessage
import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.OriginalMessage
import com.otusfoss.mail.testing.FakeDrafts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ComposeSendTest {
    private val me = MailSender("Me", "me@example.com", "a1")

    private fun TestScope.state(
        drafts: FakeDrafts = FakeDrafts(listOf(me)),
        request: ComposeRequest = ComposeRequest.New(),
        onSent: (MailSender) -> Unit = {},
    ) = ComposeState(
        ComposeServices(drafts, { _, _ -> emptyList() }),
        backgroundScope,
        request,
        ComposeOrigin("a1", listOf(ComposeAccount("a1", "Work")), listOf(me.address)),
        onSent = onSent,
        saveDelayMillis = DELAY,
    ).apply { loadIdentities() }
        .also { runCurrent() }

    @Test
    fun sendNeedsAnAddressToSendFromAndSomeoneToSendTo() =
        runTest {
            val ready = state()
            assertFalse(ready.canSend, "No recipients yet")
            ready.recipients.type(RecipientField.To, "ada@example.com,")
            assertTrue(ready.canSend)

            val noIdentity = state(FakeDrafts())
            noIdentity.recipients.type(RecipientField.Bcc, "ada@example.com,")
            assertFalse(noIdentity.canSend, "Without an identity there is no From address")
        }

    @Test
    fun sendTakesAnAddressStillBeingTypedAndRecordsTheAddressItWentFrom() =
        runTest {
            var sentFrom: MailSender? = null
            val state = state(onSent = { sentFrom = it })
            state.recipients.type(RecipientField.To, "ada@example.com")
            val outgoing = assertNotNull(state.handOff())
            assertEquals(listOf(MailSender("", "ada@example.com")), outgoing.ready().to)
            assertNull(sentFrom, "Nothing is recorded before the message went")
            outgoing.sent()
            assertEquals(me.address, sentFrom?.address)
        }

    @Test
    fun aReplyIsHandedOverAsWrittenWithItsOriginal() =
        runTest {
            val reply = ComposeRequest.Reply(original(), all = false)
            val state = state(request = reply)
            state.edit { it.copy(body = "Thanks!") }
            val outgoing = assertNotNull(state.handOff())

            val draft = outgoing.ready()
            assertEquals("Thanks!", draft.body)
            assertEquals(me, draft.from)
            assertEquals(AnsweredMessage("m1", forwarded = false), outgoing.answered)
        }

    @Test
    fun aHandOffWaitsForTheSaveInProgressAndNothingSavesAfterIt() =
        runTest {
            val answer = CompletableDeferred<Unit>()
            val drafts = FakeDrafts(listOf(me), answer)
            val state = state(drafts)
            state.recipients.type(RecipientField.To, "ada@example.com,")
            advanceTimeBy(DELAY + 1)
            assertEquals(1, drafts.saved.size, "A save is under way")

            val outgoing = assertNotNull(state.handOff())
            var ready: Draft? = null
            backgroundScope.launch { ready = outgoing.ready() }
            runCurrent()
            assertNull(ready, "The draft waits for the save to learn its copy's id")
            answer.complete(Unit)
            runCurrent()
            assertEquals("d1", ready?.emailId)

            state.edit { it.copy(subject = "Late change") }
            state.close()
            advanceTimeBy(DELAY * 2)
            assertEquals(1, drafts.saved.size, "A handed-over draft is never saved again")
        }

    @Test
    fun nothingIsHandedOverWithoutRecipients() =
        runTest {
            val state = state()
            assertNull(state.handOff(), "No recipient yet")
            state.recipients.type(RecipientField.To, "ada@example.com,")
            assertNotNull(state.handOff())
        }

    private fun original() =
        OriginalMessage(
            accountId = "a1",
            emailId = "m1",
            subject = "Lunch",
            from = MailSender("Ada", "ada@example.com"),
            to = listOf(me),
            cc = emptyList(),
            sentLabel = "2026-10-16 12:00",
            body = "Noon?",
        )

    private companion object {
        const val DELAY = 1_000L
    }
}
