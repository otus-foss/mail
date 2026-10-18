package com.otus.mail.feature.inbox.data

import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.FilterSort
import com.otus.mail.feature.inbox.domain.FolderRef
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.OwnAddress
import com.otus.mail.feature.inbox.domain.QuickFacet
import com.otus.mail.feature.inbox.domain.ReadStatus
import com.otus.mail.feature.inbox.domain.RecipientField
import com.otus.mail.feature.inbox.domain.SenderKey
import com.otus.mail.feature.inbox.domain.SizeFilter
import com.otus.mail.feature.inbox.domain.TypeFacet
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant

class EmailConditionsTest {
    private val now = Instant.parse("2026-10-18T10:30:15.250Z")
    private val zurich = TimeZone.of("Europe/Zurich")
    private val own = mapOf("ada" to setOf("a@x.org", "b@x.org"), "bo" to setOf("c@y.org"))

    private fun conditions(
        query: MailQuery,
        login: String = "ada",
    ): String? = emailConditions(query, login, now, zurich)?.let { JsonArray(it).toString() }

    private fun conditions(filters: InboxFilters): String? = conditions(MailQuery(filters, addresses = own))

    private fun dated(date: DateFilter): String? = conditions(InboxFilters(date = date))

    @Test
    fun statusAsksForTheSeenKeyword() {
        assertEquals("""[{"notKeyword":"${'$'}seen"}]""", conditions(InboxFilters(status = ReadStatus.Unread)))
        assertEquals("""[{"hasKeyword":"${'$'}seen"}]""", conditions(InboxFilters(status = ReadStatus.Read)))
        assertEquals("[]", conditions(InboxFilters(status = ReadStatus.All)))
    }

    @Test
    fun eachQuickAndTypeFacetHasItsConditionOrNone() {
        val query =
            MailQuery(
                InboxFilters(quick = QuickFacet.entries.toSet(), types = TypeFacet.entries.toSet()),
                readLaterIds = mapOf("ada" to "later"),
                addresses = own,
            )
        assertEquals(
            """[{"hasKeyword":"${'$'}flagged"},{"inMailbox":"later"},{"hasAttachment":true},""" +
                """{"hasKeyword":"${'$'}forwarded"},""" +
                """{"operator":"OR","conditions":[{"header":["List-Id"]},{"header":["List-Unsubscribe"]}]}]""",
            conditions(query),
            "Needs my reply, Awaiting reply, Conversations, and Calendar invites are checked on the loaded threads",
        )
    }

    @Test
    fun mergedSendersMatchAnyOfTheirAddresses() {
        val openAi = SenderKey("OpenAI", setOf("noreply@openai.com", "news@openai.com"))
        val grace = SenderKey.of("grace@x.org")
        assertEquals(
            """[{"operator":"OR","conditions":[{"from":"grace@x.org"},{"from":"news@openai.com"},""" +
                """{"from":"noreply@openai.com"}]}]""",
            conditions(InboxFilters(from = setOf(openAi, grace))),
        )
    }

    @Test
    fun recipientsMatchInTheChosenFieldsOnly() {
        val sentTo = setOf(OwnAddress("ada", "b@x.org"), OwnAddress("bo", "c@y.org"))
        assertEquals(
            """[{"operator":"OR","conditions":[{"to":"b@x.org"},{"cc":"b@x.org"}]}]""",
            conditions(InboxFilters(sentTo = sentTo)),
            "To and Cc by default, and only this login's own address",
        )
        val others = InboxFilters(to = setOf("sam@x.org"), receivedIn = setOf(RecipientField.Bcc))
        assertEquals("""[{"bcc":"sam@x.org"}]""", conditions(others))
        val both = InboxFilters(sentTo = sentTo, to = setOf("sam@x.org"), receivedIn = setOf(RecipientField.To))
        assertEquals(
            """[{"operator":"OR","conditions":[{"to":"b@x.org"},{"to":"sam@x.org"}]}]""",
            conditions(both),
        )
        assertEquals(
            """[{"operator":"OR","conditions":[{"to":"c@y.org"},{"to":"sam@x.org"}]}]""",
            conditions(MailQuery(both, addresses = own), "bo"),
        )
    }

    @Test
    fun onlyChangedFieldsUseTheLoginsOwnAddresses() {
        assertEquals(
            """[{"operator":"OR","conditions":[{"cc":"a@x.org"},{"cc":"b@x.org"}]}]""",
            conditions(InboxFilters(receivedIn = setOf(RecipientField.Cc))),
        )
        assertEquals(
            "[]",
            conditions(InboxFilters(receivedIn = RecipientField.Default)),
            "The default fields add nothing",
        )
    }

    @Test
    fun accountReadsOnlyThatLogin() {
        val ada = MailQuery(InboxFilters(account = "ada"))
        assertEquals("[]", conditions(ada, "ada"))
        assertNull(conditions(ada, "bo"))
    }

    @Test
    fun foldersAndSizeNarrowTheMailWithoutLeavingAnyOut() {
        val query =
            MailQuery(
                InboxFilters(folders = setOf(FolderRef("ada", "f")), size = SizeFilter.Over5Mb),
                folderIds = mapOf("ada" to setOf("f", "f2")),
            )
        val json = conditions(query)
        assertEquals("""[{"minSize":5000000}]""", json, "Folders replace the page's mailboxes in the caller")
        assertNull(conditions(query, login = "bo"))
        assertFalse("NOT" in json.orEmpty())
    }

    @Test
    fun noChoiceLeavesMailOutWithNot() {
        val everything =
            MailQuery(
                InboxFilters(
                    status = ReadStatus.Read,
                    quick = QuickFacet.entries.toSet(),
                    types = TypeFacet.entries.toSet(),
                    from = setOf(SenderKey.of("s@x.org")),
                    sentTo = setOf(OwnAddress("ada", "a@x.org")),
                    to = setOf("t@x.org"),
                    receivedIn = RecipientField.entries.toSet(),
                    account = "ada",
                    folders = setOf(FolderRef("ada", "f")),
                    date = DateFilter.ThisYear,
                    size = SizeFilter.Over1Mb,
                ),
                folderIds = mapOf("ada" to setOf("f")),
                readLaterIds = mapOf("ada" to "later"),
                addresses = own,
            )
        assertFalse("\"NOT\"" in conditions(everything).orEmpty())
    }

    @Test
    fun defaultFiltersAddNothing() {
        assertEquals("[]", conditions(MailQuery()))
    }

    @Test
    fun loginsWithoutTheirChoicesAreSkipped() {
        val sentTo = MailQuery(InboxFilters(sentTo = setOf(OwnAddress("ada", "a@x.org"))))
        assertNull(conditions(sentTo, login = "bo"))
        val folders =
            MailQuery(
                InboxFilters(folders = setOf(FolderRef("ada", "f"))),
                folderIds =
                    mapOf("ada" to setOf("f")),
            )
        assertNull(conditions(folders, login = "bo"))
        val readLater = MailQuery(InboxFilters(quick = setOf(QuickFacet.ReadLater)), readLaterIds = mapOf("ada" to "r"))
        assertNull(conditions(readLater, login = "bo"))
        assertNull(conditions(MailQuery(logins = setOf("ada")), login = "bo"))
        assertEquals("[]", conditions(MailQuery(logins = setOf("ada")), login = "ada"))
    }

    @Test
    fun datesAreWholeUtcSecondsFromTheLocalDay() {
        assertEquals("""[{"after":"2026-10-17T22:00:00Z"}]""", dated(DateFilter.Today))
        assertEquals("""[{"after":"2026-10-11T10:30:15Z"}]""", dated(DateFilter.Last7))
        assertEquals("""[{"after":"2026-09-18T10:30:15Z"}]""", dated(DateFilter.Last30))
        assertEquals("""[{"after":"2025-12-31T23:00:00Z"}]""", dated(DateFilter.ThisYear))
        val range = DateFilter.Range(LocalDate(2026, 10, 12), LocalDate(2026, 10, 18))
        assertEquals("""[{"after":"2026-10-11T22:00:00Z"},{"before":"2026-10-18T22:00:00Z"}]""", dated(range))
    }

    @Test
    fun combinedFiltersJoinTheMailboxesWithAnd() {
        val filter =
            mailFilter(
                listOf("a", "b"),
                emailConditions(MailQuery(InboxFilters(status = ReadStatus.Unread)), "ada", now, zurich)!!,
            )
        assertEquals(
            """{"operator":"AND","conditions":[""" +
                """{"operator":"OR","conditions":[{"inMailbox":"a"},{"inMailbox":"b"}]},""" +
                """{"notKeyword":"${'$'}seen"}]}""",
            filter.toString(),
        )
        assertEquals("""{"inMailbox":"a"}""", mailFilter(listOf("a"), emptyList()).toString())
    }

    @Test
    fun sortFollowsTheChoice() {
        assertEquals(false, receivedOrder(FilterSort.NewestFirst).single().isAscending)
        assertEquals(true, receivedOrder(FilterSort.OldestFirst).single().isAscending)
    }
}
