package com.otus.mail.feature.inbox.domain

import com.otus.mail.core.mailbox.MailboxRole
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class InboxFiltersTest {
    @Test
    fun activeCountCountsEachChoiceButNotTheOrder() {
        assertEquals(0, InboxFilters(sort = FilterSort.OldestFirst).activeCount)
        val filters =
            InboxFilters(
                status = ReadStatus.Unread,
                quick = setOf(QuickFacet.Flagged, QuickFacet.AwaitingReply),
                types = setOf(TypeFacet.Newsletters),
                sentTo = setOf(OwnAddress("ada", "a@x.org")),
                folders = setOf(FolderRef("ada", "f")),
                date = DateFilter.Today,
            )
        assertEquals(7, filters.activeCount)
        assertFalse(filters.isDefault)
        assertTrue(InboxFilters().isDefault)
    }

    @Test
    fun everyKindOfChoiceCountsAsActive() {
        val filters =
            InboxFilters(
                from = setOf(SenderKey("OpenAI", setOf("noreply@openai.com", "news@openai.com"))),
                to = setOf("sam@x.org"),
                account = "ada",
                receivedIn = setOf(RecipientField.Bcc),
                size = SizeFilter.Over1Mb,
            )
        assertEquals(5, filters.activeCount, "A merged sender counts once, however many addresses it has")
        assertEquals(0, InboxFilters(receivedIn = RecipientField.Default).activeCount, "To and Cc are the default")
    }

    @Test
    fun theLastRecipientFieldStaysOn() {
        val toOnly = InboxFilters().withField(RecipientField.Cc, on = false)
        assertEquals(setOf(RecipientField.To), toOnly.receivedIn)
        assertEquals(toOnly, toOnly.withField(RecipientField.To, on = false))
        assertEquals(
            setOf(RecipientField.To, RecipientField.Bcc),
            toOnly.withField(RecipientField.Bcc, on = true).receivedIn,
        )
    }

    @Test
    fun quickFiltersHaveNoOpposites() {
        val flagged = InboxFilters().with(QuickFacet.Flagged, true).with(QuickFacet.Attachments, true)
        assertEquals(setOf(QuickFacet.Flagged, QuickFacet.Attachments), flagged.quick)
        assertEquals(setOf(QuickFacet.Attachments), flagged.with(QuickFacet.Flagged, false).quick)
        assertEquals(setOf(TypeFacet.CalendarInvites), InboxFilters().with(TypeFacet.CalendarInvites, true).types)
    }

    @Test
    fun onlyThreadChoicesAreClientOnly() {
        assertFalse(InboxFilters(quick = setOf(QuickFacet.Flagged, QuickFacet.Forwarded)).clientOnly)
        assertFalse(InboxFilters(types = setOf(TypeFacet.Newsletters)).clientOnly)
        listOf(QuickFacet.NeedsMyReply, QuickFacet.AwaitingReply, QuickFacet.Conversations).forEach {
            assertTrue(InboxFilters(quick = setOf(it)).clientOnly, "$it")
        }
        assertTrue(InboxFilters(types = setOf(TypeFacet.CalendarInvites)).clientOnly)
    }

    @Test
    fun pillsEditTheStatusAndQuickFilters() {
        val unread = InboxFilters().with(InboxPill.Filter.Unread, true)
        assertEquals(ReadStatus.Unread, unread.status)
        assertTrue(unread.has(InboxPill.Filter.Unread))
        assertEquals(ReadStatus.All, unread.with(InboxPill.Filter.Unread, false).status)
        val flagged = InboxFilters().with(InboxPill.Filter.Flagged, true)
        assertTrue(flagged.has(QuickFacet.Flagged))
        assertTrue(InboxFilters().with(InboxPill.Filter.Conversations, true).has(QuickFacet.Conversations))
        assertFalse(InboxFilters(status = ReadStatus.Read).has(InboxPill.Filter.Unread))
    }

    @Test
    fun folderChoiceReplacesTheSameFolder() {
        val filters = InboxFilters().withFolder(FolderRef("ada", "f"), on = true)
        val only = filters.withFolder(FolderRef("ada", "f", includeSubfolders = false), on = true)
        assertEquals(setOf(FolderRef("ada", "f", includeSubfolders = false)), only.folders)
        assertEquals(emptySet(), only.withFolder(FolderRef("ada", "f"), on = false).folders)
    }

    @Test
    fun subfoldersExpandOnlyWhenIncluded() {
        val tree = mapOf("clients" to setOf("acme", "acme-2026"), "work" to setOf("w1"))
        val work = FolderRef("ada", "work", includeSubfolders = false)
        val refs = setOf(FolderRef("ada", "clients"), work, FolderRef("bo", "x"))
        val ids = folderIds(refs) { _, id -> tree[id].orEmpty() }
        assertEquals(mapOf("ada" to setOf("clients", "acme", "acme-2026", "work"), "bo" to setOf("x")), ids)
    }

    @Test
    fun rangesRunFromLocalMidnightThroughTheLastDay() {
        val zurich = TimeZone.of("Europe/Zurich")
        val now = Instant.parse("2026-10-25T12:00:00Z")
        // Clocks go back on 25 October, so that day lasts 25 hours.
        val bounds = DateFilter.Range(LocalDate(2026, 10, 25), LocalDate(2026, 10, 25)).bounds(now, zurich)
        assertEquals(Instant.parse("2026-10-24T22:00:00Z"), bounds.after)
        assertEquals(Instant.parse("2026-10-25T23:00:00Z"), bounds.before)
        assertEquals(DateBounds(), DateFilter.Any.bounds(now, zurich))
    }

    @Test
    fun thisYearStartsAtLocalMidnightOnTheFirstOfJanuary() {
        val zurich = TimeZone.of("Europe/Zurich")
        val bounds = DateFilter.ThisYear.bounds(Instant.parse("2026-10-18T10:00:00Z"), zurich)
        assertEquals(DateBounds(after = Instant.parse("2025-12-31T23:00:00Z")), bounds)
        // 23:30 UTC on 31 December is already the new year in Zurich.
        val newYear = DateFilter.ThisYear.bounds(Instant.parse("2026-12-31T23:30:00Z"), zurich)
        assertEquals(Instant.parse("2026-12-31T23:00:00Z"), newYear.after)
    }

    @Test
    fun theLastDaysStartAtALocalMidnightAllDay() {
        val zurich = TimeZone.of("Europe/Zurich")
        val morning = Instant.parse("2026-10-18T05:00:00Z")
        val night = Instant.parse("2026-10-18T21:59:59Z")
        assertEquals(
            DateBounds(after = Instant.parse("2026-10-11T22:00:00Z")),
            DateFilter.Last7.bounds(morning, zurich),
        )
        assertEquals(DateFilter.Last7.bounds(morning, zurich), DateFilter.Last7.bounds(night, zurich))
        assertEquals(DateBounds(after = Instant.parse("2026-09-18T22:00:00Z")), DateFilter.Last30.bounds(night, zurich))
    }

    @Test
    fun todayStartsAtLocalMidnightAcrossTheDateLine() {
        val auckland = TimeZone.of("Pacific/Auckland")
        // 11:30 UTC on 18 October is already 19 October in Auckland.
        val bounds = DateFilter.Today.bounds(Instant.parse("2026-10-18T11:30:00Z"), auckland)
        assertEquals(Instant.parse("2026-10-18T11:00:00Z"), bounds.after)
    }

    @Test
    fun loadedThreadsMeetTheReplyChoices() {
        val awaiting = message("1", ReplyState(lastFromMe = true, participated = true))
        val needsReply = message("2", ReplyState(participated = true))
        val toMe = message("3", ReplyState(lastToMe = true))
        val newsletter = message("4", ReplyState(lastToMe = true, lastIsList = true))
        val copied = message("5", ReplyState())
        val all = listOf(awaiting, needsReply, toMe, newsletter, copied)
        val awaitingFilter = InboxFilters(quick = setOf(QuickFacet.AwaitingReply))
        assertEquals(listOf(awaiting), all.filter(awaitingFilter::keepsLoaded))
        val needsFilter = InboxFilters(quick = setOf(QuickFacet.NeedsMyReply))
        assertEquals(listOf(needsReply, toMe), all.filter(needsFilter::keepsLoaded))
        assertEquals(all, all.filter(InboxFilters()::keepsLoaded))
    }

    private fun message(
        id: String,
        reply: ReplyState,
    ): MailMessage {
        val sender = MailSender("", "$id@example.org")
        val received = ReceivedAt("2026-10-18", "10:00")
        return MailMessage(id, sender, "", "", received, MailLocation("Inbox", "Ada", mailboxRole = MailboxRole.Inbox))
            .copy(
                conversation =
                    MailConversation(
                        id,
                        1,
                        ThreadEmail(sender, emptyList(), received),
                        emptyList(),
                        false,
                        emptyList(),
                        reply = reply,
                    ),
            )
    }
}
