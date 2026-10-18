package com.otus.mail.feature.inbox.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class PeopleMatchTest {
    private val grace = MailSender("Grace Hopper", "g.hopper@navy.example")
    private val ada = MailSender("", "ada_lovelace@example.com")
    private val hops = MailSender("Team", "hops@brewery.example")
    private val people = listOf(grace, ada)

    @Test
    fun matchesThePrefixOfAnyNameWord() {
        assertEquals(listOf(grace), people.matching("Hop"))
        assertEquals(listOf(grace), people.matching("grace h"))
    }

    @Test
    fun matchesTextAnywhereInTheAddress() {
        assertEquals(listOf(ada), people.matching("love"))
        assertEquals(listOf(grace), people.matching("navy"))
        assertEquals(listOf(grace, ada), people.matching("example"))
    }

    @Test
    fun matchesEachSideOfAnAt() {
        assertEquals(listOf(grace), people.matching("hop@"))
        assertEquals(listOf(grace), people.matching("hop@na"))
        assertEquals(listOf(ada), people.matching("@example.com"))
        assertEquals(emptyList(), people.matching("hop@brew"))
    }

    @Test
    fun ranksNamesAboveAddresses() = assertEquals(listOf(grace, hops), listOf(hops, grace).matching("hop"))

    @Test
    fun ignoresTextInsideANameWord() = assertEquals(emptyList(), people.matching("race"))

    @Test
    fun blankTextMatchesNobody() = assertEquals(emptyList(), people.matching("  "))
}
