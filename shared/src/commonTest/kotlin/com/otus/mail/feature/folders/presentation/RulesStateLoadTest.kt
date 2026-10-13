package com.otus.mail.feature.folders.presentation

import com.otus.mail.feature.folders.domain.AccountRules
import com.otus.mail.feature.folders.domain.ApplyOutcome
import com.otus.mail.feature.folders.domain.MailRule
import com.otus.mail.feature.folders.domain.RuleAvailability
import com.otus.mail.feature.folders.domain.RuleChange
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.RuleField
import com.otus.mail.feature.folders.domain.RuleOperator
import com.otus.mail.feature.folders.domain.RuleOutcome
import com.otus.mail.feature.folders.domain.RuleRepository
import com.otus.mail.feature.folders.domain.RuleSupport
import com.otus.mail.feature.folders.domain.without
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RulesStateLoadTest {
    /**
     * Saves every change. Each load waits for its own entry in [answers] and still finishes when
     * replaced. Tests answer every load before asserting, so a failure cannot leave one waiting.
     */
    private class SlowRules : RuleRepository {
        val answers = mutableListOf<CompletableDeferred<List<AccountRules>>>()

        override suspend fun accounts(): List<AccountRules> {
            val answer = CompletableDeferred<List<AccountRules>>()
            answers += answer
            return withContext(NonCancellable) { answer.await() }
        }

        override suspend fun save(
            account: AccountRules,
            change: RuleChange,
        ): RuleOutcome = RuleOutcome.Saved

        override suspend fun applyToInbox(
            account: AccountRules,
            rule: MailRule,
        ): ApplyOutcome = ApplyOutcome.Applied(0, 0, more = false)
    }

    private fun accounts(vararg ruleIds: String): List<AccountRules> {
        val condition = RuleCondition(RuleField.From, RuleOperator.Contains, "amazon")
        val available = RuleAvailability.Available(RuleSupport(setOf("fileinto", "mailboxid")))
        val rules = ruleIds.map { MailRule(it, it, listOf(condition), "M7") }
        return listOf(AccountRules("ada", "Ada", available, rules = rules, installed = true))
    }

    private fun TestScope.loaded(repository: SlowRules): RulesState {
        val rules = RulesState(repository, this)
        rules.refresh()
        runCurrent()
        repository.answers.single().complete(accounts("r1", "r2"))
        runCurrent()
        return rules
    }

    private fun RulesState.ruleIds() = account("ada")?.rules?.map { it.id }

    @Test
    fun aLoadStartedBeforeASaveCannotBringBackTheOldRules() =
        runTest {
            val repository = SlowRules()
            val rules = loaded(repository)
            rules.refresh()
            runCurrent()
            rules.updateRules("ada") { it.without("r1") }
            runCurrent()
            repository.answers[2].complete(accounts("r2"))
            runCurrent()
            val busyAfterSave = rules.busy
            // This load read the rules before the save.
            repository.answers[1].complete(accounts("r1", "r2"))
            runCurrent()
            assertFalse(busyAfterSave)
            assertEquals(listOf("r2"), rules.ruleIds())
            assertFalse(rules.loading)
        }

    @Test
    fun aSaveStaysBusyUntilTheNewestLoadEnds() =
        runTest {
            val repository = SlowRules()
            val rules = loaded(repository)
            rules.updateRules("ada") { it.without("r1") }
            runCurrent()
            rules.refresh()
            runCurrent()
            repository.answers[1].complete(accounts("r2"))
            runCurrent()
            val busyAfterReplaced = rules.busy
            val loadingAfterReplaced = rules.loading
            repository.answers[2].complete(accounts("r2"))
            runCurrent()
            assertTrue(busyAfterReplaced, "The next change must not build on rules a newer load replaces")
            assertTrue(loadingAfterReplaced)
            assertFalse(rules.busy)
            assertFalse(rules.loading)
            assertEquals(listOf("r2"), rules.ruleIds())
        }
}
