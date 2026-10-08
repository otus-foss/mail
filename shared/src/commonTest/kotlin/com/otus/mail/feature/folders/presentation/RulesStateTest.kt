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
    ) = AccountRules(
        "ada",
        "Ada",
        RuleAvailability.Available(support),
        installed = installed,
        activeOther = activeOther,
    )

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
            assertEquals(RuleChange(emptyList(), keep = "roundcube", run = true), repository.changes.last())
            repository.account = account(installed = true, activeOther = "vacation")
            rules.refresh()
            runCurrent()
            rules.turnOn("ada", keepOther = true)
            runCurrent()
            assertEquals(RuleChange(emptyList(), keep = null, run = true), repository.changes.last())
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
}
