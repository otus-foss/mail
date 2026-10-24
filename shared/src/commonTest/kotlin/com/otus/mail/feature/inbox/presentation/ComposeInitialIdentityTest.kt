package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.DraftRepository
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.SendIdentity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ComposeInitialIdentityTest {
    @Test
    fun recentIdentitySelectsItsAccountBeforeSaving() =
        runTest {
            val work = SendIdentity("1", MailSender("Work", "work@example.com", "work"))
            val home = SendIdentity("1", MailSender("Home", "home@example.com", "home"))
            val saved = FakeDrafts()
            val drafts =
                object : DraftRepository by saved {
                    override suspend fun identities(accountId: String): List<SendIdentity> =
                        listOf(work, home).filter { it.accountId == accountId }
                }
            val state =
                ComposeState(
                    ComposeServices(drafts, { _, _, _ -> error("No sending") }, { _, _ -> emptyList() }),
                    backgroundScope,
                    ComposeRequest.New(),
                    ComposeOrigin(
                        "work",
                        listOf(ComposeAccount("work", "Work"), ComposeAccount("home", "Home")),
                        listOf(work.sender.address, home.sender.address),
                        recentFrom = listOf(home.sender.address),
                    ),
                )

            state.loadIdentities()
            runCurrent()
            assertEquals(home.sender, state.draft.from)
            assertEquals("home", state.draft.accountId)
            state.edit { it.copy(body = "Hello") }
            state.close()
            runCurrent()
            assertEquals("home", saved.saved.single().accountId)
        }
}
