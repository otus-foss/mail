package com.otus.mail.feature.compose.presentation

import com.otus.mail.core.mail.EmailAddress
import com.otus.mail.core.mail.EmailBodyPart
import com.otus.mail.core.mail.EmailBodyValue
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.DraftRepository
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.SendIdentity
import com.otus.mail.core.messages.domain.SentMessage
import com.otus.mail.feature.compose.data.toDraft
import com.otus.mail.testing.FakeDrafts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A saved copy opened again in Write keeps its signature, even while replies leave it out. */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedSignatureTest {
    private val text = EmailBodyPart(partId = "t", type = "text/plain")
    private val html = EmailBodyPart(partId = "h", type = "text/html")

    private fun saved(
        plain: String,
        formatted: String = "",
    ) = EmailObject(
        id = "d1",
        receivedAt = "2026-11-10T10:00:00Z",
        from = listOf(EmailAddress("Me", "me@example.com")),
        textBody = listOf(text),
        htmlBody = listOf(html),
        bodyValues = mapOf("t" to EmailBodyValue(plain), "h" to EmailBodyValue(formatted)),
    ).toDraft("a1", whole = false)

    private fun TestScope.opened(request: ComposeRequest) =
        ComposeState(
            ComposeServices(Signed(), { _, _ -> emptyList() }),
            backgroundScope,
            request,
            ComposeOrigin(
                "a1",
                listOf(ComposeAccount("a1", "Work")),
                listOf("me@example.com"),
                signatureInReplies = false,
            ),
        ).also {
            it.loadIdentities()
            runCurrent()
        }

    @Test
    fun aReopenedDraftKeepsItsSignature() =
        runTest {
            val draft = saved("Hi\n\n-- \nWork", "Hi<br><div class=\"otus-signature\">Work</div>")
            assertEquals("Hi", draft.body)
            assertEquals("Hi", draft.html)
            val state = opened(ComposeRequest.Resume(draft, ComposeRequest.New("a1"), asSaved = true))
            assertTrue(state.signatureOn)
            assertEquals("Work", state.signature.text)
        }

    @Test
    fun aReopenedReplyDraftKeepsItsSignatureAndQuote() =
        runTest {
            val draft = saved("Sure\n\n-- \nWork\n\nOn Monday Ada wrote:\n> Noon?")
            assertEquals("Sure", draft.body)
            assertEquals("On Monday Ada wrote:\n> Noon?", draft.quote)
            val state = opened(ComposeRequest.Resume(draft, ComposeRequest.New("a1"), asSaved = true))
            assertTrue(state.signatureOn)
            assertEquals("Work", state.signature.text)
        }

    @Test
    fun anEditedScheduledMailKeepsItsSignature() =
        runTest {
            val draft = saved("Dinner at 8\n\n-- \nWork")
            val held = SentMessage("a1", "d1", "s1")
            val state = opened(ComposeRequest.Resume(draft, ComposeRequest.New("a1"), held = held))
            assertTrue(state.signatureOn)
            assertEquals(
                "Work",
                state
                    .reopening()
                    .draft.signature.text,
                "Undo opens it signed too",
            )
        }

    @Test
    fun aDraftSavedWithoutASignatureStaysUnsigned() =
        runTest {
            val state = opened(ComposeRequest.Resume(saved("Hi"), ComposeRequest.New("a1"), asSaved = true))
            assertFalse(state.signatureOn)
            assertTrue(state.signature.isEmpty)
        }

    private class Signed : DraftRepository by FakeDrafts() {
        override suspend fun identities(accountId: String) = listOf(SendIdentity("w", me, textSignature = "Work"))
    }

    private companion object {
        val me = MailSender("Me", "me@example.com", "a1")
    }
}
