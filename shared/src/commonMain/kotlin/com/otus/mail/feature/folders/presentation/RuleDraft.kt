package com.otus.mail.feature.folders.presentation

import com.otus.mail.feature.folders.domain.MailRule
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.RuleField
import com.otus.mail.feature.folders.domain.RuleOperator
import kotlin.random.Random

/** A rule being edited. [ruleId] is null until a new rule is saved. */
data class RuleDraft(
    val accountId: String,
    val ruleId: String?,
    val name: String = "",
    val matchAll: Boolean = true,
    val conditions: List<RuleCondition> = listOf(RuleCondition(RuleField.From, RuleOperator.Contains, "")),
    val folderId: String? = null,
    val markRead: Boolean = false,
    val enabled: Boolean = true,
    /** Also move matching mail that is already in the inbox, once, after saving. */
    val applyToInbox: Boolean = false,
) {
    fun isFor(
        accountId: String,
        ruleId: String?,
    ): Boolean = this.accountId == accountId && this.ruleId == ruleId

    /** The rule to save. Names are one line; a blank name stays blank and the summary is shown instead. */
    fun toRule(id: String = ruleId ?: newRuleId()): MailRule =
        MailRule(
            id = id,
            name = name.replace(Regex("[\\r\\n]+"), " ").trim(),
            conditions = conditions.map { it.copy(value = it.value.trim()) },
            folderId = folderId.orEmpty(),
            matchAll = matchAll,
            markRead = markRead,
            enabled = enabled,
        )

    companion object {
        fun of(
            accountId: String,
            rule: MailRule,
        ) = RuleDraft(
            accountId,
            rule.id,
            rule.name,
            rule.matchAll,
            rule.conditions,
            rule.folderId,
            rule.markRead,
            rule.enabled,
        )
    }
}

/** Ids only need to be unique within one account's rules. */
private fun newRuleId(): String = Random.nextLong().toULong().toString(RADIX)

private const val RADIX = 36
