package com.otus.mail.feature.folders.presentation

import com.otus.mail.feature.folders.domain.AccountRules
import com.otus.mail.feature.folders.domain.ApplyOutcome
import com.otus.mail.feature.folders.domain.FolderStorageException
import com.otus.mail.feature.folders.domain.MailRule
import com.otus.mail.feature.folders.domain.RuleAvailability
import com.otus.mail.feature.folders.domain.RuleChange
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.RuleFailure
import com.otus.mail.feature.folders.domain.RuleField
import com.otus.mail.feature.folders.domain.RuleOperator
import com.otus.mail.feature.folders.domain.RuleOutcome
import com.otus.mail.feature.folders.domain.RuleRepository
import com.otus.mail.feature.folders.domain.RuleSupport
import com.otus.mail.feature.folders.domain.moved
import com.otus.mail.feature.folders.domain.paused
import com.otus.mail.feature.folders.domain.withRule
import com.otus.mail.feature.folders.domain.without
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RulesStateTest {
    private val support = RuleSupport(setOf("fileinto", "mailboxid", "include"))
    private val condition = RuleCondition(RuleField.From, RuleOperator.Contains, "amazon")

    /** Records each change and answers with [outcome]; the account reloads as [account]. */
    private class FakeRules(
        var account: AccountRules,
        var outcome: RuleOutcome = RuleOutcome.Saved,
    ) : RuleRepository {
        val changes = mutableListOf<RuleChange>()
        val applied = mutableListOf<MailRule>()
        var storageFails = false

        override suspend fun accounts() = listOf(account)

        override suspend fun save(
            account: AccountRules,
            change: RuleChange,
        ): RuleOutcome {
            if (storageFails) throw FolderStorageException(IllegalStateException("unreadable"))
            return outcome.also { changes += change }
        }

        override suspend fun applyToInbox(
            account: AccountRules,
            rule: MailRule,
        ): ApplyOutcome = ApplyOutcome.Applied(2, 40, more = false).also { applied += rule }
    }

    private fun account(
        installed: Boolean = false,
        activeOther: String? = null,
        keep: String? = null,
        changedElsewhere: Boolean = false,
        rules: List<MailRule> = emptyList(),
    ) = AccountRules(
        "ada",
        "Ada",
        RuleAvailability.Available(support),
        rules = rules,
        installed = installed,
        activeOther = activeOther,
        keep = keep,
        changedElsewhere = changedElsewhere,
    )

    private fun rule(id: String) = MailRule(id, id, listOf(condition), "M7")

    private fun TestScope.loaded(repository: FakeRules): Pair<RulesState, RuleEditorState> {
        val rules = RulesState(repository, this)
        rules.refresh()
        runCurrent()
        return rules to RuleEditorState(rules)
    }

    @Test
    fun aFirstRuleRunsWhenNothingElseDoes() =
        runTest {
            val repository = FakeRules(account())
            val (_, editor) = loaded(repository)
            editor.edit("ada", null)
            editor.update(
                requireNotNull(editor.draft).copy(conditions = listOf(condition), folderId = "M7", applyToInbox = true),
            )
            var saved = false
            editor.save { saved = true }
            runCurrent()
            val change = repository.changes.single()
            assertEquals(true, change.run)
            assertEquals("M7", change.rules.single().folderId)
            assertEquals(1, repository.applied.size, "Matching inbox mail moves after saving")
            assertTrue(saved)
            assertNull(editor.draft)
        }

    @Test
    fun anotherRunningScriptIsNeverReplacedWithoutAsking() =
        runTest {
            val repository = FakeRules(account(activeOther = "roundcube"))
            val (_, editor) = loaded(repository)
            editor.edit("ada", null)
            editor.update(requireNotNull(editor.draft).copy(conditions = listOf(condition), folderId = "M7"))
            editor.save {}
            runCurrent()
            assertNull(repository.changes.single().run, "Activation stays as it is")
        }

    @Test
    fun turningOnKeepsAnotherScriptButNeverAVacationReply() =
        runTest {
            val repository = FakeRules(account(installed = true, activeOther = "roundcube"))
            val (rules, _) = loaded(repository)
            rules.turnOn("ada", keepOther = true)
            runCurrent()
            assertEquals("roundcube", repository.changes.last().keep)
            repository.account = account(installed = true, activeOther = "vacation")
            rules.refresh()
            runCurrent()
            rules.turnOn("ada", keepOther = true)
            runCurrent()
            assertEquals(null, repository.changes.last().keep)
        }

    @Test
    fun replacingTheRunningScriptStopsEveryScriptKeptBefore() =
        runTest {
            val repository = FakeRules(account(installed = true, activeOther = "roundcube", keep = "roundcube"))
            val (rules, _) = loaded(repository)
            rules.turnOn("ada", keepOther = false)
            runCurrent()
            assertEquals(null, repository.changes.single().keep)
            repository.account = account(installed = true, activeOther = "vacation", keep = "vacation")
            rules.refresh()
            runCurrent()
            rules.turnOn("ada", keepOther = true)
            runCurrent()
            assertEquals(null, repository.changes.last().keep, "A vacation reply is never kept")
        }

    @Test
    fun turningRulesOnWithNothingElseRunningKeepsTheScriptKeptBefore() =
        runTest {
            val repository = FakeRules(account(installed = true, keep = "roundcube"))
            val (rules, _) = loaded(repository)
            rules.turnOn("ada", keepOther = false)
            runCurrent()
            assertEquals("roundcube", repository.changes.single().keep)
        }

    @Test
    fun everyChangeAsksBeforeReplacingEditsMadeElsewhere() =
        runTest {
            val repository =
                FakeRules(
                    account(
                        installed = true,
                        activeOther = "roundcube",
                        keep = "x",
                        changedElsewhere = true,
                        rules = listOf(rule("r1"), rule("r2")),
                    ),
                )
            val (rules, editor) = loaded(repository)
            val changes =
                listOf<() -> Unit>(
                    { rules.updateRules("ada") { it.paused("r1", true) } },
                    { rules.updateRules("ada") { it.moved("r2", -1) } },
                    { rules.updateRules("ada") { it.without("r1") } },
                    { rules.turnOn("ada", keepOther = false) },
                    { rules.turnOff("ada") },
                    { rules.stopKeeping("ada") },
                    {
                        editor.edit("ada", "r1")
                        editor.save {}
                    },
                )
            changes.forEach { change ->
                change()
                runCurrent()
                val request = assertNotNull(rules.replaceRequest)
                assertTrue(repository.changes.isEmpty(), "Nothing is saved before the user replaces the edits")
                request.dismiss()
                assertNull(rules.replaceRequest)
            }
            rules.updateRules("ada") { it.without("r1") }
            assertNotNull(rules.replaceRequest).replace()
            runCurrent()
            assertNull(rules.replaceRequest)
            val saved = repository.changes.single()
            assertEquals(listOf("r2"), saved.rules.map { it.id })
        }

    @Test
    fun unreadableAccountStorageIsReportedInsteadOfCrashing() =
        runTest {
            val repository = FakeRules(account(installed = true))
            val (rules, _) = loaded(repository)
            repository.storageFails = true
            rules.turnOff("ada")
            runCurrent()
            assertEquals(STORAGE_NOTICE, rules.notice)
            assertTrue(!rules.busy)
        }

    @Test
    fun aRefusedSaveKeepsTheDraftAndExplains() =
        runTest {
            val repository = FakeRules(account(), RuleOutcome.Failed(RuleFailure.ChangedElsewhere))
            val (rules, editor) = loaded(repository)
            editor.edit("ada", null)
            editor.update(requireNotNull(editor.draft).copy(conditions = listOf(condition), folderId = "M7"))
            editor.save { error("Not saved") }
            runCurrent()
            assertNotNull(editor.draft)
            assertEquals(RuleFailure.ChangedElsewhere.message(), rules.notice)
        }

    @Test
    fun anAddedRuleIsSavedAndARefusalReachesTheCaller() =
        runTest {
            val repository = FakeRules(account(installed = true))
            val (rules, _) = loaded(repository)
            val failures = mutableListOf<String>()
            val rule = MailRule("q", "Move mail from amazon", listOf(condition), "r")
            rules.submit("ada", onFailed = { failures += it }) { it.withRule(rule) }
            runCurrent()
            assertEquals(listOf(rule), repository.changes.single().rules)
            assertNull(repository.changes.single().run)
            assertTrue(failures.isEmpty())

            repository.outcome = RuleOutcome.Failed(RuleFailure.TooLarge)
            rules.submit("ada", onFailed = { failures += it }) { it.withRule(rule.copy(id = "q2")) }
            runCurrent()
            assertEquals(listOf(RuleFailure.TooLarge.message()), failures)
            assertEquals(failures.single(), rules.notice)

            rules.submit("grace", onFailed = { failures += it }) { it.withRule(rule) }
            assertEquals(RuleFailure.NotFound.message(), failures.last())
        }

    @Test
    fun aChangeFromOutsideTheRulesPageLeavesRulesEditedElsewhereAlone() =
        runTest {
            val repository = FakeRules(account(installed = true).copy(changedElsewhere = true))
            val (rules, _) = loaded(repository)
            val failures = mutableListOf<String>()
            rules.submit(
                "ada",
                onFailed = { failures += it },
            ) { it.withRule(MailRule("q", "", listOf(condition), "r")) }
            runCurrent()
            assertTrue(repository.changes.isEmpty())
            assertNull(rules.replaceRequest)
            assertEquals(1, failures.size)
        }
}
