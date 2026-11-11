package com.otusfoss.mail.feature.compose.presentation

import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.core.messages.domain.DraftRepository
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.SendIdentity
import com.otusfoss.mail.testing.FakeDrafts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ComposeInitialIdentityTest {
    private val work = SendIdentity("1", MailSender("Work", "work@example.com", "work"))
    private val home = SendIdentity("1", MailSender("Home", "home@example.com", "home"))

    @Test
    fun recentIdentitySelectsItsAccountBeforeSaving() =
        runTest {
            val saved = FakeDrafts()
            val state = state(saved)

            state.loadIdentities()
            runCurrent()
            assertEquals(home.sender, state.draft.from)
            assertEquals("home", state.draft.accountId)
            state.edit { it.copy(body = "Hello") }
            state.close()
            runCurrent()
            assertEquals("home", saved.saved.single().accountId)
        }

    @Test
    fun typingBeforeIdentitiesLoadMovesTheEarlyCopyToTheChosenAccount() =
        runTest {
            val answer = CompletableDeferred<Unit>()
            val loaded = CompletableDeferred<Unit>()
            val saved = FakeDrafts(answer = answer)
            val state = state(saved, loaded)

            state.loadIdentities()
            state.edit { it.copy(body = "Hello") }
            advanceTimeBy(DELAY + 1)
            loaded.complete(Unit)
            runCurrent()
            assertEquals("home", state.draft.accountId)
            answer.complete(Unit)
            runCurrent()
            state.close()
            runCurrent()
            assertEquals(listOf("work", "home"), saved.saved.map { it.accountId })
            assertEquals(listOf("d1"), saved.discarded)
            assertEquals("d2", state.draft.emailId)
        }

    private fun TestScope.state(
        saved: FakeDrafts,
        loaded: CompletableDeferred<Unit> = CompletableDeferred(Unit),
    ): ComposeState {
        val drafts =
            object : DraftRepository by saved {
                override suspend fun identities(accountId: String): List<SendIdentity> {
                    loaded.await()
                    return listOf(work, home).filter { it.accountId == accountId }
                }
            }
        return ComposeState(
            ComposeServices(drafts, { _, _ -> emptyList() }),
            backgroundScope,
            ComposeRequest.New(),
            ComposeOrigin(
                "work",
                listOf(ComposeAccount("work", "Work"), ComposeAccount("home", "Home")),
                listOf(work.sender.address, home.sender.address),
                recentFrom = listOf(home.sender.address),
            ),
            saveDelayMillis = DELAY,
        )
    }

    private companion object {
        const val DELAY = 1_000L
    }
}
