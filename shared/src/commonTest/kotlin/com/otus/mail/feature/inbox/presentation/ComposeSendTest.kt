package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.AnsweredMessage
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.DraftSender
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.OriginalMessage
import com.otus.mail.feature.inbox.domain.SendException
import com.otus.mail.feature.inbox.domain.SendFailure
import com.otus.mail.feature.inbox.domain.SentMessage
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
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ComposeSendTest {
    private class FakeSender(
        var failure: SendFailure? = null,
    ) : DraftSender {
        val sent = mutableListOf<Pair<Draft, AnsweredMessage?>>()

        override suspend fun send(
            draft: Draft,
            answered: AnsweredMessage?,
            at: Instant?,
        ): Result<SentMessage> {
            failure?.let { return Result.failure(SendException(it)) }
            sent += draft to answered
            return Result.success(SentMessage(draft.accountId, "e1", "s1"))
        }
    }

    private val me = MailSender("Me", "me@example.com", "a1")

    private fun TestScope.state(
        drafts: FakeDrafts = FakeDrafts(listOf(me)),
        sender: DraftSender = FakeSender(),
        request: ComposeRequest = ComposeRequest.New(),
        onSent: (MailSender) -> Unit = {},
    ) = ComposeState(
        ComposeServices(drafts, sender, { _, _ -> emptyList() }),
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
            val sender = FakeSender()
            var sentFrom: MailSender? = null
            val state = state(sender = sender, onSent = { sentFrom = it })
            state.recipients.type(RecipientField.To, "ada@example.com")
            val send = assertNotNull(state.handOff())
            send(null)
            assertEquals(
                listOf(MailSender("", "ada@example.com")),
                sender.sent
                    .single()
                    .first.to,
            )
            assertEquals(me.address, sentFrom?.address)
        }

    @Test
    fun aReplyIsSentAsWrittenWithItsOriginal() =
        runTest {
            val sender = FakeSender()
            val reply = ComposeRequest.Reply(original(), all = false)
            val state = state(sender = sender, request = reply)
            state.edit { it.copy(body = "Thanks!") }
            assertEquals(SentMessage("a1", "e1", "s1"), assertNotNull(state.handOff())(null).getOrThrow())

            val (draft, answered) = sender.sent.single()
            assertEquals("Thanks!", draft.body)
            assertEquals(me, draft.from)
            assertEquals(AnsweredMessage("m1", forwarded = false), answered)
        }

    @Test
    fun aSendWaitsForTheSaveInProgressAndNothingSavesAfterIt() =
        runTest {
            val answer = CompletableDeferred<Unit>()
            val drafts = FakeDrafts(listOf(me), answer)
            val sender = FakeSender()
            val state = state(drafts, sender)
            state.recipients.type(RecipientField.To, "ada@example.com,")
            advanceTimeBy(DELAY + 1)
            assertEquals(1, drafts.saved.size, "A save is under way")

            val send = assertNotNull(state.handOff())
            backgroundScope.launch { send(null) }
            runCurrent()
            assertTrue(sender.sent.isEmpty(), "Sending waits for the save to learn its copy's id")
            answer.complete(Unit)
            runCurrent()
            assertEquals(
                "d1",
                sender.sent
                    .single()
                    .first.emailId,
            )

            state.edit { it.copy(subject = "Late change") }
            state.close()
            advanceTimeBy(DELAY * 2)
            assertEquals(1, drafts.saved.size, "A sent draft is never saved again")
        }

    @Test
    fun aFailedSendCanRunAgainAndNothingSendsWithoutRecipients() =
        runTest {
            val sender = FakeSender(SendFailure.Unreachable)
            val state = state(sender = sender)
            assertNull(state.handOff(), "No recipient yet")
            state.recipients.type(RecipientField.To, "ada@example.com,")
            val send = assertNotNull(state.handOff())
            assertTrue(send(null).isFailure)
            sender.failure = null
            assertTrue(send(null).isSuccess)
            assertEquals(1, sender.sent.size)
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
