package com.otus.mail.feature.inbox.data

import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.FilterSort
import com.otus.mail.feature.inbox.domain.FolderRef
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.OwnAddress
import com.otus.mail.feature.inbox.domain.RecipientField
import com.otus.mail.feature.inbox.domain.ShowFacet
import com.otus.mail.feature.inbox.domain.SizeFilter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class EmailConditionsTest {
    private val now = Instant.parse("2026-10-18T10:30:15.250Z")
    private val zurich = TimeZone.of("Europe/Zurich")

    private fun conditions(
        query: MailQuery,
        login: String = "ada",
    ): String? = emailConditions(query, login, now, zurich)?.let { JsonArray(it).toString() }

    private fun dated(date: DateFilter): String? = conditions(MailQuery(InboxFilters(date = date)))

    @Test
    fun eachShowFacetHasItsCondition() {
        val query =
            MailQuery(
                InboxFilters(show = ShowFacet.entries.toSet()),
                readLaterIds = mapOf("ada" to "later"),
                addresses = mapOf("ada" to setOf("ada@example.org"), "bo" to setOf("bo@example.org")),
            )
        assertEquals(
            """[{"notKeyword":"${'$'}seen"},{"hasKeyword":"${'$'}seen"},{"hasKeyword":"${'$'}flagged"},""" +
                """{"notKeyword":"${'$'}flagged"},{"inMailbox":"later"},{"hasAttachment":true},""" +
                """{"hasKeyword":"${'$'}answered"},{"notKeyword":"${'$'}answered"},""" +
                """{"hasKeyword":"${'$'}forwarded"},""" +
                """{"from":"ada@example.org"},""" +
                """{"operator":"OR","conditions":[{"header":["List-Id"]},{"header":["List-Unsubscribe"]}]}]""",
            conditions(query),
        )
    }

    @Test
    fun receivedAsUsesTheLoginsOwnAddressesUnlessSomeAreChosen() {
        val addresses = mapOf("ada" to setOf("a@x.org", "b@x.org"))
        val cc = MailQuery(InboxFilters(receivedAs = setOf(RecipientField.Cc)), addresses = addresses)
        assertEquals("""[{"operator":"OR","conditions":[{"cc":"a@x.org"},{"cc":"b@x.org"}]}]""", conditions(cc))
        val chosen =
            cc.copy(
                filters =
                    cc.filters.copy(
                        sentTo = setOf(OwnAddress("ada", "b@x.org")),
                        receivedAs = setOf(RecipientField.To, RecipientField.Bcc),
                    ),
            )
        assertEquals("""[{"operator":"OR","conditions":[{"to":"b@x.org"},{"bcc":"b@x.org"}]}]""", conditions(chosen))
    }

    @Test
    fun sendersSkippedFoldersAndSizeNarrowTheMail() {
        val query =
            MailQuery(
                InboxFilters(
                    from = setOf("sam@x.org"),
                    size = SizeFilter.Over5Mb,
                    skipFolders = setOf(FolderRef("ada", "t")),
                ),
                skipIds = mapOf("ada" to setOf("t", "t2")),
            )
        assertEquals(
            """[{"from":"sam@x.org"},{"operator":"NOT","conditions":[{"inMailbox":"t"},{"inMailbox":"t2"}]},""" +
                """{"minSize":5000000}]""",
            conditions(query),
        )
        assertNull(conditions(MailQuery(InboxFilters(accounts = setOf("bo")))), "Accounts not chosen are skipped")
    }

    @Test
    fun defaultFiltersAddNothing() {
        assertEquals("[]", conditions(MailQuery()))
    }

    @Test
    fun sentToMatchesAnyAddressInToCcOrBcc() {
        val addresses = setOf(OwnAddress("ada", "a@x.org"), OwnAddress("ada", "b@x.org"), OwnAddress("bo", "c@y.org"))
        val filters = InboxFilters(sentTo = addresses)
        assertEquals(
            """[{"operator":"OR","conditions":[{"to":"a@x.org"},{"cc":"a@x.org"},{"bcc":"a@x.org"},""" +
                """{"to":"b@x.org"},{"cc":"b@x.org"},{"bcc":"b@x.org"}]}]""",
            conditions(MailQuery(filters)),
        )
    }

    @Test
    fun loginsWithoutTheirChoicesAreSkipped() {
        val sentTo = MailQuery(InboxFilters(sentTo = setOf(OwnAddress("ada", "a@x.org"))))
        assertNull(conditions(sentTo, login = "bo"))
        val folders =
            MailQuery(
                InboxFilters(folders = setOf(FolderRef("ada", "f"))),
                folderIds = mapOf("ada" to setOf("f")),
            )
        assertNull(conditions(folders, login = "bo"))
        val readLater =
            MailQuery(InboxFilters(show = setOf(ShowFacet.ReadLater)), readLaterIds = mapOf("ada" to "r"))
        assertNull(conditions(readLater, login = "bo"))
        assertNull(conditions(MailQuery(logins = setOf("ada")), login = "bo"))
        assertEquals("[]", conditions(MailQuery(logins = setOf("ada")), login = "ada"))
    }

    @Test
    fun datesAreWholeUtcSecondsFromTheLocalDay() {
        assertEquals("""[{"after":"2026-10-17T22:00:00Z"}]""", dated(DateFilter.Today))
        assertEquals("""[{"after":"2026-10-11T10:30:15Z"}]""", dated(DateFilter.Last7))
        assertEquals("""[{"after":"2026-09-18T10:30:15Z"}]""", dated(DateFilter.Last30))
        val range = DateFilter.Range(LocalDate(2026, 10, 12), LocalDate(2026, 10, 18))
        assertEquals(
            """[{"after":"2026-10-11T22:00:00Z"},{"before":"2026-10-18T22:00:00Z"}]""",
            dated(range),
        )
    }

    @Test
    fun combinedFiltersJoinTheMailboxesWithAnd() {
        val filter =
            mailFilter(
                listOf("a", "b"),
                emailConditions(MailQuery(InboxFilters(show = setOf(ShowFacet.Unread))), "ada", now, zurich)!!,
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
