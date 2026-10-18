package com.otus.mail.feature.inbox.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class PeopleMatchTest {
    private val grace = MailSender("Grace Hopper", "g.hopper@navy.example")
    private val ada = MailSender("", "ada_lovelace@example.com")
    private val people = listOf(grace, ada)

    @Test
    fun matchesThePrefixOfAnyNameWord() {
        assertEquals(listOf(grace), people.matching("Hop"))
        assertEquals(listOf(grace), people.matching("grace h"))
    }

    @Test
    fun matchesThePartsOfAnAddressBeforeTheAt() {
        assertEquals(listOf(ada), people.matching("love"))
        assertEquals(listOf(grace), people.matching("g.hop"))
    }

    @Test
    fun ignoresTextInsideAWord() {
        assertEquals(emptyList(), people.matching("opper"))
        assertEquals(emptyList(), people.matching("navy"))
    }

    @Test
    fun blankTextMatchesNobody() = assertEquals(emptyList(), people.matching("  "))
}
