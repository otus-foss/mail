package com.otus.mail.feature.contacts.domain

import com.otus.jscontact.Address
import com.otus.jscontact.Card
import com.otus.jscontact.EmailAddress
import com.otus.jscontact.Name
import com.otus.jscontact.PersonalInfo
import com.otus.jscontact.PersonalInfoKind
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class MergePlanTest {
    private val home = BookKey("ada", "a", "home")
    private val work = BookKey("ada", "a", "work")

    private val base =
        ContactCard(
            CardKey("ada", "a", "c1"),
            Card(
                id = "c1",
                name = Name(full = "Ada Lovelace"),
                emails = mapOf("e1" to EmailAddress("ada@home.test")),
                addresses = mapOf("a1" to Address(full = "London")),
                addressBookIds = mapOf("home" to true, "work" to true),
            ),
            listOf(home, work),
        )

    private val other =
        ContactCard(
            CardKey("ada", "a", "c2"),
            Card(
                id = "c2",
                emails = mapOf("e1" to EmailAddress("ada@work.test")),
                addresses = mapOf("a1" to Address(full = "Ockham"), "a2" to Address(full = "London")),
                personalInfo = mapOf("p1" to PersonalInfo(PersonalInfoKind.Hobby, "Poetry")),
                extra = mapOf("example.com:pet" to JsonPrimitive("Cat")),
            ),
            listOf(home),
        )

    private val plan = MergePlan(listOf(base, other), base.key, setOf("ada@home.test", "ada@work.test"), home)

    @Test
    fun theMergedCardKeepsEveryValueOfTheOtherCard() {
        val merged = plan.merged()
        assertEquals(
            mapOf("a1" to Address(full = "London"), "a1-1" to Address(full = "Ockham")),
            merged.addresses,
        )
        assertEquals(other.card.personalInfo, merged.personalInfo)
        assertEquals(other.card.extra, merged.extra)
        assertEquals(listOf("ada@home.test", "ada@work.test"), merged.emails?.values?.map { it.address })
    }

    @Test
    fun theMergedCardStaysInEachBookOfTheTarget() {
        assertEquals(mapOf("home" to true, "work" to true), plan.merged().addressBookIds)
    }
}
