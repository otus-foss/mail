package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.SendIdentity
import com.otus.mail.feature.inbox.domain.Signature
import com.otus.mail.feature.inbox.domain.signature
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SignaturesStateTest {
    private val sales = MailSender("Sales", "sales@companya.com", "a1")
    private val support = MailSender("Support", "support@companya.com", "a1")
    private val saved = mutableListOf<Pair<String, Signature>>()
    private var failing = false

    private fun TestScope.state() =
        SignaturesState(
            FakeDrafts(listOf(sales, support)),
            { identity, signature ->
                if (failing) {
                    Result.failure(IllegalStateException())
                } else {
                    Result.success(Unit).also {
                        saved +=
                            identity.id to signature
                    }
                }
            },
            null,
            backgroundScope,
        ) { listOf(ComposeAccount("a1", "Company A")) }
            .apply { load() }
            .also { runCurrent() }

    @Test
    fun copyingToOthersSavesTheSignatureOnEachOfThem() =
        runTest {
            val state = state()
            val source = state.identity("sales@companya.com")!!.copy(textSignature = "Company A Sales")
            state.copy(source, listOf(state.identity("support@companya.com")!!))
            runCurrent()
            assertEquals(listOf("support@companya.com" to Signature(text = "Company A Sales")), saved)
            assertEquals("Company A Sales", state.identity("support@companya.com")?.signature?.text)
            assertNull(state.notice)
        }

    @Test
    fun aFailedSavePutsTheOldSignatureBackAndSaysSo() =
        runTest {
            val state = state()
            failing = true
            state.save(state.identity("sales@companya.com")!!, Signature(text = "New"))
            runCurrent()
            assertEquals("", state.identity("sales@companya.com")?.textSignature)
            assertNotNull(state.notice)
        }

    private fun SendIdentity.copy(textSignature: String) = SendIdentity(id, sender, textSignature, htmlSignature)
}
