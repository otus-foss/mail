package com.otus.mail.feature.folders.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class RuleListsTest {
    private fun rule(id: String) = MailRule(id, id, emptyList(), "f")

    private val rules = listOf(rule("a"), rule("b"), rule("c"))

    @Test
    fun rulesMoveWithinTheListAndStayPutAtItsEnds() {
        assertEquals(listOf("b", "a", "c"), rules.moved("b", -1).map { it.id })
        assertEquals(listOf("a", "c", "b"), rules.moved("b", 1).map { it.id })
        assertEquals(rules, rules.moved("a", -1))
        assertEquals(rules, rules.moved("missing", 1))
    }

    @Test
    fun savingReplacesByIdOrAppends() {
        val renamed = rule("b").copy(name = "Bills")
        assertEquals(listOf("a", "Bills", "c"), rules.upserted(renamed).map { it.name })
        assertEquals(listOf("a", "b", "c", "d"), rules.upserted(rule("d")).map { it.id })
        assertEquals(listOf("a", "c"), rules.without("b").map { it.id })
        assertEquals(listOf(true, false, true), rules.paused("b", true).map { it.enabled })
    }
}
