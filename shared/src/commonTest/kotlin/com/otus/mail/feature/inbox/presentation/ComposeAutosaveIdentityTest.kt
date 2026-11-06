package com.otus.mail.feature.inbox.presentation

import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.Draft
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.SendIdentity
import com.otus.mail.feature.inbox.domain.DraftRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ComposeAutosaveIdentityTest {
    @Test
    fun changingOnlyTheIdentityOrSignatureSavesAnExistingDraft() =
        runTest {
            val first = SendIdentity("1", MailSender("Me", "me@example.com", "a"), textSignature = "First")
            val second = SendIdentity("2", MailSender("Me", "alias@example.com", "a"), textSignature = "Second")
            val saved = FakeDrafts()
            val drafts =
                object : DraftRepository by saved {
                    override suspend fun identities(accountId: String) = listOf(first, second)
                }
            val state =
                ComposeState(
                    ComposeServices(drafts, { _, _ -> emptyList() }),
                    backgroundScope,
                    ComposeRequest.Resume(
                        Draft("a", from = first.sender, body = "Hello", emailId = "existing"),
                        ComposeRequest.New(),
                    ),
                    ComposeOrigin("a", listOf(ComposeAccount("a", "A")), emptyList()),
                )
            state.loadIdentities()
            runCurrent()
            state.chooseFrom(second)
            state.close()
            runCurrent()
            assertEquals(second.sender, saved.saved.single().from)
            assertEquals(
                "Second",
                saved.saved
                    .single()
                    .signature.text,
            )

            state.showSignature(false)
            state.close()
            runCurrent()
            assertTrue(
                saved.saved
                    .last()
                    .signature.isEmpty,
            )
            assertEquals(2, saved.saved.size)

            state.close()
            runCurrent()
            assertEquals(2, saved.saved.size)
        }
}
