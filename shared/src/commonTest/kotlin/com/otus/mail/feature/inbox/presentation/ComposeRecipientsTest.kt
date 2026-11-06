package com.otus.mail.feature.inbox.presentation

import com.otus.mail.core.messages.domain.MailSender
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ComposeRecipientsTest {
    private val ada = MailSender("Ada", "ada@example.com")
    private val grace = MailSender("Grace Hopper", "grace@example.com")
    private var line = emptyList<MailSender>()

    private fun TestScope.recipients() =
        ComposeRecipients(
            { _, query -> listOf(ada, grace).filter { query in it.name.lowercase() || query in it.address } },
            backgroundScope,
            accountId = { "a1" },
            people = { line },
            change = { _, people -> line = people },
        )

    @Test
    fun aCommaOrNextTurnsTypedAddressesIntoChipsAndKeepsTextThatIsNotOne() =
        runTest {
            val recipients = recipients()
            recipients.type(RecipientField.To, "Ada <ada@example.com>,")
            assertEquals(listOf(ada), line)
            assertEquals("", recipients.typed.getValue(RecipientField.To))
            recipients.type(RecipientField.To, "grac")
            recipients.commit(RecipientField.To)
            assertEquals("grac", recipients.typed.getValue(RecipientField.To))
        }

    @Test
    fun tappingAChipTakesItBackToEditAndBackspaceRemovesTheLast() =
        runTest {
            val recipients = recipients()
            line = listOf(ada, grace)
            recipients.edit(RecipientField.To, 0)
            assertEquals(listOf(grace), line)
            assertEquals("Ada <ada@example.com>", recipients.typed.getValue(RecipientField.To))
            recipients.removeLast(RecipientField.To)
            assertEquals(emptyList(), line)
        }

    @Test
    fun suggestionsLeaveOutPeopleAlreadyOnTheLine() =
        runTest {
            val recipients = recipients()
            line = listOf(ada)
            recipients.type(RecipientField.Cc, "a")
            advanceTimeBy(SUGGEST_WAIT)
            runCurrent()
            assertEquals(ComposeRecipients.Suggestions(RecipientField.Cc, listOf(grace)), recipients.suggestions)
            recipients.choose(RecipientField.Cc, grace)
            assertEquals(listOf(ada, grace), line)
            assertEquals(ComposeRecipients.Suggestions(), recipients.suggestions)
        }

    private companion object {
        const val SUGGEST_WAIT = 300L
    }
}
