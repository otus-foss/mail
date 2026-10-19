package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.FoundPerson
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.OwnAddress
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.domain.SenderKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterPeopleTest {
    private fun message(
        id: Int,
        sender: MailSender,
        day: Int = 18,
        to: List<MailSender> = emptyList(),
        cc: List<MailSender> = emptyList(),
    ) = MailMessage(
        "$id",
        sender,
        "",
        "",
        ReceivedAt("2026-10-${day.toString().padStart(2, '0')}", "09:00"),
        MailLocation("Inbox", "Ada", "ada", "ada@example.org"),
        to = to,
        cc = cc,
    )

    private val noreply = MailSender("OpenAI", "noreply@openai.com")
    private val news = MailSender("OpenAI", "News@OpenAI.com")
    private val sam = MailSender("Sam", "sam@x.org")
    private val kim = MailSender("Kim", "kim@x.org")

    @Test
    fun sendersWithTheSameNameAndDomainMerge() {
        val people = fromPeople(listOf(message(1, noreply), message(2, news), message(3, noreply), message(4, sam)))
        val openAi = people.first()
        assertEquals("OpenAI", openAi.name)
        assertEquals(listOf("noreply@openai.com", "News@OpenAI.com"), openAi.addresses, "The most used address first")
        assertEquals(3, openAi.conversations)
        assertEquals(SenderKey("OpenAI", setOf("noreply@openai.com", "news@openai.com")), openAi.key)
        val other = MailSender("OpenAI", "noreply@example.org")
        assertEquals(
            2,
            fromPeople(listOf(message(1, noreply), message(2, other))).size,
            "Another domain is someone else",
        )
    }

    @Test
    fun namelessSendersStayApart() {
        val first = MailSender("", "a@x.org")
        val second = MailSender("", "b@x.org")
        assertEquals(2, fromPeople(listOf(message(1, first), message(2, second))).size)
    }

    @Test
    fun theMostFrequentComeFirstThenTheMostRecent() {
        val messages =
            listOf(
                message(1, sam, day = 10),
                message(2, kim, day = 12),
                message(3, sam, day = 11),
                message(4, noreply, day = 15),
            )
        assertEquals(listOf("Sam", "OpenAI", "Kim"), fromPeople(messages).map { it.name })
    }

    @Test
    fun searchResultsJoinTheLoadedSenders() {
        val found =
            listOf(
                FoundPerson(MailSender("Grace Hopper", "grace@x.org"), 4, "2026-09-01T10:00"),
                FoundPerson(MailSender("", "sam@x.org"), 1, "2026-01-01T10:00"),
                FoundPerson(MailSender("Lin", "lin@x.org")),
            )
        val people = fromPeople(listOf(message(1, sam), message(2, sam)), found, query = "")
        assertEquals(listOf("Grace Hopper", "Sam", "Lin"), people.map { it.label })
        assertEquals(2, people[1].conversations, "The loaded rows count, not the smaller search")
        val searched = fromPeople(listOf(message(1, sam), message(2, kim)), found.take(1), query = "gra")
        assertEquals(listOf("Grace Hopper"), searched.map { it.name }, "Loaded senders must match the text too")
    }

    @Test
    fun pickingAPersonPicksAllTheirAddresses() {
        val openAi = fromPeople(listOf(message(1, noreply), message(2, news))).single()
        val picked = InboxFilters().withSender(openAi, on = true)
        assertEquals(setOf(openAi.key), picked.from)
        assertTrue(picked.hasSender(openAi))
        val lone = InboxFilters(from = setOf(SenderKey.of("noreply@openai.com")))
        assertTrue(lone.hasSender(openAi), "A sender saved by one of the addresses counts as picked")
        assertEquals(setOf(openAi.key), lone.withSender(openAi, on = true).from, "Picking replaces the lone address")
        assertFalse(picked.withSender(openAi, on = false).hasSender(openAi))
    }

    @Test
    fun theToPageListsOwnAddressesFirst() {
        val own = listOf(OwnAddress("ada", "ada@example.org"), OwnAddress("ada", "hi@example.org"))
        val me = MailSender("Ada", "Ada@example.org")
        val messages =
            listOf(
                message(1, sam, to = listOf(me), cc = listOf(kim)),
                message(2, sam, to = listOf(kim)),
                message(3, sam, to = listOf(me)),
            )
        val people = toPeople(messages, own, mapOf("ada" to "Work"))
        assertEquals(listOf("ada@example.org", "hi@example.org", "kim@x.org"), people.map { it.addresses.first() })
        assertEquals(listOf(2, 0, 2), people.map { it.conversations })
        assertEquals("Work", people.first().accountName)
        assertEquals(own.first(), people.first().own)
        val picked = InboxFilters().withRecipient(people[0], true).withRecipient(people[2], true)
        assertEquals(setOf(own.first()), picked.sentTo)
        assertEquals(setOf("kim@x.org"), picked.to)
        assertTrue(picked.hasRecipient(people[2]))
        assertFalse(picked.withRecipient(people[0], false).hasRecipient(people[0]))
    }

    @Test
    fun pickingARecipientPicksAllTheirAddresses() {
        val openAi =
            toPeople(listOf(message(1, sam, to = listOf(noreply)), message(2, sam, to = listOf(news))), emptyList())
        val picked = InboxFilters().withRecipient(openAi.single(), on = true)
        assertEquals(setOf(noreply.address, news.address), picked.to)
        assertEquals(emptySet(), picked.withRecipient(openAi.single(), on = false).to)
    }

    @Test
    fun anOwnAddressSavedInAnotherCaseStillCountsAsPicked() {
        val own = OwnAddress("ada", "ada@example.org")
        val person = FilterPerson("Work", listOf(own.address), own = own)
        val saved = InboxFilters(sentTo = setOf(OwnAddress("ada", "Ada@Example.org")))
        assertTrue(saved.hasRecipient(person))
        assertEquals(emptySet(), saved.withRecipient(person, on = false).sentTo)
    }
}
