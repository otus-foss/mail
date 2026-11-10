package com.otus.mail.feature.compose.presentation

import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.Draft
import com.otus.mail.core.messages.domain.DraftRepository
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.SendIdentity
import com.otus.mail.core.messages.domain.SentMessage
import com.otus.mail.core.messages.domain.Signature
import com.otus.mail.testing.FakeDrafts
import com.otus.mail.testing.reopened
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A saved copy opened again in Write keeps the signature it had, whatever the reply setting says. */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedSignatureTest {
    private val work = SendIdentity("w", MailSender("Me", "me@example.com", "a1"), textSignature = "Work")
    private val alias = SendIdentity("a", MailSender("Me", "alias@example.com", "a1"), textSignature = "Alias")
    private val bare = SendIdentity("b", MailSender("Me", "bare@example.com", "a1"))
    private val signed = Draft("a1", from = work.sender, body = "Hi", signature = Signature(text = "Work"))
    private val quote = "On Monday, Ada wrote:\n> Noon?"

    private fun TestScope.opened(
        draft: Draft,
        inReplies: Boolean = false,
        drafts: DraftRepository = Identities(listOf(work, alias, bare)),
        held: SentMessage? = null,
    ) = ComposeState(
        ComposeServices(drafts, { _, _ -> emptyList() }),
        backgroundScope,
        ComposeRequest.Resume(draft.reopened(), ComposeRequest.New("a1"), asSaved = held == null, held = held),
        ComposeOrigin(
            "a1",
            listOf(ComposeAccount("a1", "Work")),
            listOf("me@example.com"),
            signatureInReplies = inReplies,
        ),
    ).also {
        it.loadIdentities()
        runCurrent()
    }

    @Test
    fun aReopenedDraftKeepsItsSignature() =
        runTest {
            val state = opened(signed)
            assertTrue(state.signatureOn)
            assertEquals("Work", state.signature.text)
        }

    @Test
    fun aReopenedReplyDraftKeepsItsSignatureAndQuote() =
        runTest {
            val state = opened(signed.copy(quote = quote))
            assertEquals("Work", state.signature.text)
            assertEquals(quote, state.draft.quote)
        }

    @Test
    fun anEditedScheduledMailKeepsItsSignatureThroughUndo() =
        runTest {
            val state = opened(signed, held = SentMessage("a1", "d1", "s1"))
            assertEquals("Work", state.signature.text)
            assertEquals(
                "Work",
                state
                    .reopening()
                    .draft.signature.text,
            )
        }

    @Test
    fun anUnsignedCopyStaysUnsignedWhileRepliesAreSigned() =
        runTest {
            val state = opened(signed.copy(signature = Signature()), inReplies = true)
            assertFalse(state.signatureOn)
            assertTrue(state.signature.isEmpty)
        }

    @Test
    fun anotherIdentityBringsItsOwnSignature() =
        runTest {
            val state = opened(signed)
            state.chooseFrom(alias)
            assertEquals("Alias", state.signature.text)
        }

    @Test
    fun aCopyFromAnIdentityWithoutASignatureSignsLikeANewMessage() =
        runTest {
            val state = opened(Draft("a1", from = bare.sender, body = "Hi"))
            state.chooseFrom(work)
            assertEquals("Work", state.signature.text)
            assertFalse(opened(Draft("a1", from = bare.sender, body = "Hi", quote = quote)).signatureOn)
        }

    @Test
    fun aCopySavedWithoutAnAddressSignsLikeANewMessage() =
        runTest {
            val state = opened(Draft("a1", body = "Hi"))
            assertEquals(work.sender, state.draft.from)
            assertEquals("Work", state.signature.text)
        }

    @Test
    fun aSignedCopyStaysSignedWhenIdentitiesFailToLoad() =
        runTest {
            val drafts = FakeDrafts()
            val state = opened(signed, drafts = drafts)
            state.edit { it.copy(subject = "Lunch") }
            state.close()
            runCurrent()
            assertEquals(
                "Work",
                drafts.saved
                    .last()
                    .signature.text,
            )
        }

    private class Identities(
        private val all: List<SendIdentity>,
    ) : DraftRepository by FakeDrafts() {
        override suspend fun identities(accountId: String) = all.filter { it.accountId == accountId }
    }
}
