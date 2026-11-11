package com.otusfoss.mail.feature.folders.presentation

import com.otusfoss.mail.core.mailbox.MailFolder
import com.otusfoss.mail.feature.folders.data.BlockFolder
import com.otusfoss.mail.feature.folders.data.RuleBlock
import com.otusfoss.mail.feature.folders.data.blockText
import com.otusfoss.mail.feature.folders.data.decodeBlock
import com.otusfoss.mail.feature.folders.data.encodeBlock
import com.otusfoss.mail.feature.folders.data.spliceBlock
import com.otusfoss.mail.feature.folders.domain.AccountFolderRules
import com.otusfoss.mail.feature.folders.domain.AddressField
import com.otusfoss.mail.feature.folders.domain.AddressMatch
import com.otusfoss.mail.feature.folders.domain.FolderRule
import com.otusfoss.mail.feature.folders.domain.FolderRuleRepository
import com.otusfoss.mail.feature.folders.domain.RuleAvailability
import com.otusfoss.mail.feature.folders.domain.RuleCondition
import com.otusfoss.mail.feature.folders.domain.RuleFailure
import com.otusfoss.mail.feature.folders.domain.RuleOutcome
import com.otusfoss.mail.feature.folders.domain.RuleScriptFacts
import com.otusfoss.mail.feature.folders.domain.RuleSupport
import com.otusfoss.mail.feature.folders.domain.TextField
import com.otusfoss.mail.feature.folders.domain.TextOperator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FolderRulesStateTest {
    /** One account whose script is real Sieve text, saved and read back with the block codec. */
    private class ScriptServer : FolderRuleRepository {
        val support = RuleSupport(setOf("fileinto", "mailboxid"))
        var script = "require \"vacation\";\r\n# Mine\nvacation \"Away\";\n"
        var state = 1
        var refusal: RuleOutcome.Failed? = null

        override suspend fun accounts(): List<AccountFolderRules> {
            val block = blockText(script)
            val read = block?.let { decodeBlock(it, support) }
            val rules =
                read
                    ?.block
                    ?.folders
                    .orEmpty()
                    .associate { it.id to it.rules }
            val facts =
                RuleScriptFacts("S1", "Mine", true, block != null, read?.changedElsewhere == true, false, "s$state")
            return listOf(AccountFolderRules("a", "Ada", RuleAvailability.Available(support), rules, facts))
        }

        override suspend fun save(
            account: AccountFolderRules,
            rules: Map<String, List<FolderRule>>,
        ): RuleOutcome {
            val refused = refusal
            return when {
                refused != null -> {
                    refused
                }

                account.script?.state != "s$state" -> {
                    RuleOutcome.Failed(RuleFailure.ChangedElsewhere)
                }

                else -> {
                    val folders = rules.map { (id, folderRules) -> BlockFolder(id, id, folderRules) }
                    script = spliceBlock(script, encodeBlock(RuleBlock(folders), support))
                    state++
                    RuleOutcome.Saved
                }
            }
        }

        override suspend fun existingCount(
            accountId: String,
            folderId: String,
            rule: FolderRule,
        ): Int = 4

        override suspend fun moveExisting(
            accountId: String,
            folderId: String,
            rule: FolderRule,
        ): Int? = null
    }

    private val projects = MailFolder("a", "P", "Projects")
    private val jana = FolderRule(RuleCondition.Address(AddressField.From, AddressMatch.Exact("jana@agency.example")))
    private val invoices =
        FolderRule(
            listOf(
                RuleCondition.Address(AddressField.From, AddressMatch.Domain("agency.example")),
                RuleCondition.Text(TextField.Subject, TextOperator.Contains, "Invoice"),
            ),
        )

    private fun TestScope.loaded(server: ScriptServer): FolderRulesState {
        val state = FolderRulesState(server, this)
        state.refresh()
        advanceUntilIdle()
        return state
    }

    private fun FolderRulesState.chips() = rules(projects).map { rule -> rule.chipTexts().map { it.value } }

    @Test
    fun addingEditingAndDeletingSurviveASieveRoundTrip() =
        runTest {
            val server = ScriptServer()
            val handWritten = server.script
            val state = loaded(server)

            state.add(projects, jana)
            advanceUntilIdle()
            state.add(projects, invoices)
            advanceUntilIdle()
            assertEquals(listOf(listOf("jana@agency.example"), listOf("@agency.example", "“Invoice”")), state.chips())
            assertEquals(listOf(jana, invoices), loaded(server).rules(projects), "A fresh load reads the same rules")

            val spam = invoices.copy(conditions = invoices.conditions.take(1) + RuleCondition.Spam(5))
            state.replace(projects, 1, spam)
            advanceUntilIdle()
            assertEquals(listOf(jana, spam), state.rules(projects))

            state.remove(projects, 0)
            advanceUntilIdle()
            state.remove(projects, 0)
            advanceUntilIdle()
            assertEquals(emptyList(), state.rules(projects))
            assertEquals(handWritten, server.script.replace(blockText(server.script).orEmpty(), ""))
            assertFalse(state.busy)
            assertNull(state.notice)
        }

    @Test
    fun rulesEditedElsewhereAskBeforeTheyAreReplaced() =
        runTest {
            val server = ScriptServer()
            val state = loaded(server)
            state.add(projects, jana)
            advanceUntilIdle()
            server.script = server.script.replace("\"jana@agency.example\" {", "\"grace@agency.example\" {")
            state.refresh()
            advanceUntilIdle()
            assertTrue(state.account("a")?.changedElsewhere == true)

            state.add(projects, invoices)
            advanceUntilIdle()
            val request = assertNotNull(state.replaceRequest)
            assertEquals(listOf(jana), state.rules(projects), "Nothing is saved before the user agrees")
            request.replace()
            advanceUntilIdle()
            assertNull(state.replaceRequest)
            assertEquals(listOf(jana, invoices), state.rules(projects))
            assertFalse(state.account("a")?.changedElsewhere == true)
        }

    @Test
    fun aRefusedScriptKeepsTheRulesAndShowsTheServersReason() =
        runTest {
            val server = ScriptServer()
            val state = loaded(server)
            server.refusal = RuleOutcome.Failed(RuleFailure.Invalid, "line 3: unknown test")
            state.add(projects, jana)
            advanceUntilIdle()
            assertEquals("The server refused these rules: line 3: unknown test. Nothing was changed.", state.notice)
            assertEquals(emptyList(), state.rules(projects))
            assertEquals(4, state.existingCount(projects, jana))
            var moved: Int? = -1
            state.moveExisting(projects, jana) { moved = it }
            advanceUntilIdle()
            assertNull(moved)
        }
}
