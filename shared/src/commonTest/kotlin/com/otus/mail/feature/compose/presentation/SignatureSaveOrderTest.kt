package com.otus.mail.feature.compose.presentation

import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.SendIdentityKey
import com.otus.mail.core.messages.domain.Signature
import com.otus.mail.core.messages.domain.SignatureImage
import com.otus.mail.feature.compose.domain.SignatureRepository
import com.otus.mail.testing.FakeDrafts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class SignatureSaveOrderTest {
    private fun TestScope.state(repository: SignatureRepository): SignaturesState =
        SignaturesState(
            FakeDrafts(listOf(MailSender("A", "a@example.com", "a"), MailSender("B", "b@example.com", "a"))),
            repository,
            null,
            backgroundScope,
        ) { listOf(ComposeAccount("a", "A")) }.apply { load() }

    @Test
    fun pendingEditsWaitForEarlierWritesAndAFailureKeepsTheLastAcknowledgedValue() =
        runTest {
            val first = CompletableDeferred<Unit>()
            val calls = mutableListOf<String>()
            val state =
                state { _, value ->
                    calls += value.text
                    if (value.text == "first") {
                        first.await()
                        Result.success(Unit)
                    } else {
                        Result.failure(IllegalStateException())
                    }
                }
            runCurrent()
            val identity = state.identity(SendIdentityKey("a", "a@example.com"))!!
            state.save(identity, Signature(text = "first"))
            runCurrent()
            state.save(identity, Signature(text = "second"))
            runCurrent()
            assertEquals(listOf("first"), calls)
            first.complete(Unit)
            runCurrent()
            assertEquals(listOf("first", "second"), calls)
            assertEquals("first", state.identity(identity.key)?.textSignature)
            assertNotNull(state.notice)
        }

    @Test
    fun failureOnOneIdentityDoesNotRollbackAnother() =
        runTest {
            val state =
                state { identity, _ ->
                    if (identity.id.startsWith("a")) Result.failure(IllegalStateException()) else Result.success(Unit)
                }
            runCurrent()
            val targets = state.identities
            state.copy(targets.first().copy(textSignature = "new"), targets)
            runCurrent()
            assertEquals("", state.identity(SendIdentityKey("a", "a@example.com"))?.textSignature)
            assertEquals("new", state.identity(SendIdentityKey("a", "b@example.com"))?.textSignature)
        }

    @Test
    fun closingAnEditorRetriesAFailedSaveWithoutAnotherEdit() =
        runTest {
            var attempts = 0
            val state =
                state { _, _ ->
                    attempts += 1
                    if (attempts == 1) Result.failure(IllegalStateException()) else Result.success(Unit)
                }
            runCurrent()
            val editor = SignatureEditorState(state, state.identities.first())
            editor.addImage(SignatureImage("image/png", "AQID", 96))
            editor.close()
            runCurrent()
            editor.close()
            runCurrent()
            assertEquals(2, attempts)
            editor.close()
            runCurrent()
            assertEquals(2, attempts)
        }

    @Test
    fun openingAnAcknowledgedSignatureDoesNotSaveTextNormalization() =
        runTest {
            val attempts = mutableListOf<Signature>()
            val state =
                state { _, signature ->
                    attempts += signature
                    Result.success(Unit)
                }
            runCurrent()
            val signature = Signature("<p>Alex</p>", "Alex\nRubylabs")
            state.save(state.identities.first(), signature)
            runCurrent()
            val editor = SignatureEditorState(state, state.identities.first())
            editor.close()
            runCurrent()
            assertEquals(listOf(signature), attempts)
        }

    @Test
    fun returningToAnIdentityDoesNotTreatItsPendingEditAsSaved() =
        runTest {
            val finish = CompletableDeferred<Unit>()
            val attempts = mutableListOf<Signature>()
            val state =
                state { _, signature ->
                    attempts += signature
                    if (attempts.size == 1) {
                        finish.await()
                        Result.failure(IllegalStateException())
                    } else {
                        Result.success(Unit)
                    }
                }
            runCurrent()
            val editor = SignatureEditorState(state, state.identity(SendIdentityKey("a", "a@example.com"))!!)
            editor.text.setHtml("<p>Keep this edit</p>")
            editor.close()
            runCurrent()
            editor.switchTo(state.identity(SendIdentityKey("a", "b@example.com"))!!)
            editor.switchTo(state.identity(SendIdentityKey("a", "a@example.com"))!!)
            finish.complete(Unit)
            runCurrent()

            editor.close()
            runCurrent()
            assertEquals(2, attempts.size)
            assertEquals(attempts.first(), attempts.last())
            assertEquals(attempts.last(), state.savedSignature(editor.identity))
        }
}
