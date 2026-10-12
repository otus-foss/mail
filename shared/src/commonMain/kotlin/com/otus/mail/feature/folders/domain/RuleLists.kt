package com.otus.mail.feature.folders.domain

/** [rule] replaces the rule with its id, or comes last when it is new. */
fun List<MailRule>.upserted(rule: MailRule): List<MailRule> =
    if (any { it.id == rule.id }) map { if (it.id == rule.id) rule else it } else this + rule

/**
 * These rules with [rule] saved into them. A first rule turns the rules on unless another script
 * runs: replacing that is the user's choice.
 */
fun AccountRules.withRule(rule: MailRule): RuleChange =
    RuleChange(rules.upserted(rule), keep, run = true.takeIf { !installed && activeOther == null })

fun List<MailRule>.without(ruleId: String): List<MailRule> = filterNot { it.id == ruleId }

fun List<MailRule>.paused(
    ruleId: String,
    paused: Boolean,
): List<MailRule> = map { if (it.id == ruleId) it.copy(enabled = !paused) else it }

/** Moves a rule earlier (negative [offset]) or later. Order matters: the first matching rule wins. */
fun List<MailRule>.moved(
    ruleId: String,
    offset: Int,
): List<MailRule> {
    val from = indexOfFirst { it.id == ruleId }
    if (from < 0) return this
    val rules = toMutableList()
    rules.add((from + offset).coerceIn(0, lastIndex), rules.removeAt(from))
    return rules
}
