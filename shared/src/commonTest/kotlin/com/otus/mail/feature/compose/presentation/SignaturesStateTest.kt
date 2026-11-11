package com.otus.mail.feature.compose.presentation

import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.SendIdentity
import com.otus.mail.core.messages.domain.SendIdentityKey
import com.otus.mail.core.messages.domain.Signature
import com.otus.mail.core.messages.domain.signature
import com.otus.mail.testing.FakeDrafts
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
    private val sales = MailSender("Sales", "sales@companya.example", "a1")
    private val support = MailSender("Support", "support@companya.example", "a1")
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
            val source = state.identity(SendIdentityKey("a1", sales.address))!!.copy(textSignature = "Company A Sales")
            state.copy(source, listOf(state.identity(SendIdentityKey("a1", support.address))!!))
            runCurrent()
            assertEquals(listOf("support@companya.example" to Signature(text = "Company A Sales")), saved)
            assertEquals("Company A Sales", state.identity(SendIdentityKey("a1", support.address))?.signature?.text)
            assertNull(state.notice)
        }

    @Test
    fun aFailedSavePutsTheOldSignatureBackAndSaysSo() =
        runTest {
            val state = state()
            failing = true
            state.save(state.identity(SendIdentityKey("a1", sales.address))!!, Signature(text = "New"))
            runCurrent()
            assertEquals("", state.identity(SendIdentityKey("a1", sales.address))?.textSignature)
            assertNotNull(state.notice)
        }

    private fun SendIdentity.copy(textSignature: String) = SendIdentity(id, sender, textSignature, htmlSignature)
}
