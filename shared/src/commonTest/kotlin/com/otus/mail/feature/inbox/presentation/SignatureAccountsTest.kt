package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.DraftRepository
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.SendIdentity
import com.otus.mail.feature.inbox.domain.SendIdentityKey
import com.otus.mail.feature.inbox.domain.Signature
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class SignatureAccountsTest {
    private val first = SendIdentity("1", MailSender("Me", "shared@example.org", "a"), "Work", "<p>Work</p>")
    private val second = SendIdentity("1", MailSender("Me", "shared@example.org", "b"), "Home", "<p>Home</p>")
    private val saved = mutableListOf<Pair<SendIdentityKey, Signature>>()
    private val accounts = listOf(ComposeAccount("a", "Work"), ComposeAccount("b", "Home"))
    private val drafts =
        object : DraftRepository by FakeDrafts() {
            override suspend fun identities(accountId: String): List<SendIdentity> =
                listOf(first, second).filter { it.accountId == accountId }
        }

    @Test
    fun editingAnIdentityDoesNotChangeTheSameIdInAnotherAccount() =
        runTest {
            val signatures = state()
            val editor = SignatureEditorState(signatures, assertNotNull(signatures.identity(second.key)))
            editor.text.setHtml("<p>New home signature</p>")
            editor.close()
            runCurrent()

            assertEquals(listOf(second.key), saved.map { it.first })
            assertEquals("Work", signatures.identity(first.key)?.textSignature)
            assertEquals("<p>New home signature</p>", signatures.identity(second.key)?.htmlSignature)
        }

    @Test
    fun copyingAcrossAccountsWithTheSameIdentityIdChangesOnlyTheTarget() =
        runTest {
            val signatures = state()
            signatures.copy(first, listOf(second))
            runCurrent()

            assertEquals(listOf(second.key to Signature(first.htmlSignature, first.textSignature)), saved)
            assertEquals("Work", signatures.identity(first.key)?.textSignature)
            assertEquals("Work", signatures.identity(second.key)?.textSignature)
        }

    @Test
    fun changingAccountsWithTheSameSenderAddressSelectsThatAccountsSignature() =
        runTest {
            val compose =
                ComposeState(
                    ComposeServices(drafts, { _, _ -> emptyList() }),
                    backgroundScope,
                    ComposeRequest.New("a"),
                    ComposeOrigin("a", accounts, emptyList()),
                )
            compose.loadIdentities()
            runCurrent()
            assertEquals("Work", compose.signature.text)
            compose.chooseFrom(second)
            assertEquals("Home", compose.signature.text)
        }

    private fun TestScope.state(): SignaturesState =
        SignaturesState(
            drafts,
            { identity, signature ->
                saved += identity.key to signature
                Result.success(Unit)
            },
            null,
            backgroundScope,
        ) { accounts }.apply {
            load()
            runCurrent()
        }
}
