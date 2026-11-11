package com.otusfoss.mail.feature.contacts.domain

import com.otusfoss.core.jscontact.Address
import com.otusfoss.core.jscontact.Card
import com.otusfoss.core.jscontact.EmailAddress
import com.otusfoss.core.jscontact.Name
import com.otusfoss.core.jscontact.PersonalInfo
import com.otusfoss.core.jscontact.PersonalInfoKind
import com.otusfoss.core.jscontact.Relation
import com.otusfoss.core.jscontact.RelationType
import kotlinx.serialization.json.JsonObject
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
    fun setsAndRelationsJoinByKey() {
        val friend = mapOf(RelationType.Friend to true)
        val plan =
            plan(
                base.card.copy(keywords = mapOf("work" to true), relatedTo = mapOf("u1" to Relation(friend))),
                other.card.copy(
                    keywords = mapOf("golf" to true),
                    relatedTo =
                        mapOf("u2" to Relation(friend), "u1" to Relation(mapOf(RelationType.Colleague to true))),
                ),
            )
        val merged = plan.merged()
        assertEquals(mapOf("work" to true, "golf" to true), merged.keywords)
        assertEquals(
            mapOf("u1" to Relation(friend + (RelationType.Colleague to true)), "u2" to Relation(friend)),
            merged.relatedTo,
        )
    }

    @Test
    fun aLocalizationKeepsOnlyTheValuesTheMergeKeptUnderTheirKeys() {
        val patch =
            JsonObject(
                mapOf(
                    "addresses/a1/full" to JsonPrimitive("Ockham, Surrey"),
                    "personalInfo/p1/value" to JsonPrimitive("Poesie"),
                ),
            )
        val merged = plan(base.card, other.card.copy(localizations = mapOf("de" to patch))).merged()
        assertEquals(
            mapOf("de" to JsonObject(mapOf("personalInfo/p1/value" to JsonPrimitive("Poesie")))),
            merged.localizations,
        )
    }

    @Test
    fun theMergedCardStaysInEachBookOfTheTarget() {
        assertEquals(mapOf("home" to true, "work" to true), plan.merged().addressBookIds)
    }

    private fun plan(
        baseCard: Card,
        otherCard: Card,
    ) = MergePlan(
        listOf(base.copy(card = baseCard), other.copy(card = otherCard)),
        base.key,
        setOf("ada@home.test", "ada@work.test"),
        home,
    )
}
