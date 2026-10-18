package com.otus.mail.feature.inbox.domain

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
                show = setOf(ShowFacet.Unread, ShowFacet.Flagged),
                sentTo = setOf(OwnAddress("ada", "a@x.org")),
                folders = setOf(FolderRef("ada", "f")),
                date = DateFilter.Today,
            )
        assertEquals(5, filters.activeCount)
        assertFalse(filters.isDefault)
        assertTrue(InboxFilters().isDefault)
    }

    @Test
    fun oppositeChoicesTurnEachOtherOff() {
        val read = InboxFilters().with(ShowFacet.Unread, true).with(ShowFacet.Read, true)
        assertEquals(setOf(ShowFacet.Read), read.show)
        val flagged =
            InboxFilters()
                .with(
                    ShowFacet.NotFlagged,
                    true,
                ).with(ShowFacet.Flagged, true)
                .with(ShowFacet.Replied, true)
        assertEquals(setOf(ShowFacet.Flagged, ShowFacet.Replied), flagged.show)
    }

    @Test
    fun everyKindOfChoiceCountsAsActive() {
        val filters =
            InboxFilters(
                from = setOf("sam@x.org"),
                accounts = setOf("ada"),
                receivedAs = setOf(RecipientField.Cc),
                skipFolders = setOf(FolderRef("ada", "t")),
                size = SizeFilter.Over1Mb,
            )
        assertEquals(5, filters.activeCount)
        assertEquals(
            setOf(FolderRef("ada", "t", false)),
            filters.withSkipped(FolderRef("ada", "t", false), true).skipFolders,
        )
    }

    @Test
    fun filterLayoutsSurviveSavingAndSkipUnknownValues() {
        val layout =
            FilterLayout(
                listOf(FilterSection.Date, FilterSection.Show),
                listOf(ShowFacet.Newsletters, ShowFacet.Unread),
            )
        assertEquals(layout, FilterLayout.decode(FilterLayout.encode(layout)))
        assertEquals(FilterLayout(listOf(FilterSection.Size), emptyList()), FilterLayout.decode("size,later|"))
        assertEquals(FilterLayout(), FilterLayout.decode("garbage"))
    }

    @Test
    fun pillsEditTheSameShowChoices() {
        val on = InboxFilters().with(InboxPill.Filter.Unread, true)
        assertTrue(on.has(ShowFacet.Unread))
        assertTrue(on.has(InboxPill.Filter.Unread))
        assertFalse(on.with(InboxPill.Filter.Unread, false).has(ShowFacet.Unread))
    }

    @Test
    fun folderChoiceReplacesTheSameFolder() {
        val filters = InboxFilters().withFolder(FolderRef("ada", "f"), on = true)
        val only = filters.withFolder(FolderRef("ada", "f", includeSubfolders = false), on = true)
        assertEquals(setOf(FolderRef("ada", "f", includeSubfolders = false)), only.folders)
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
    fun todayStartsAtLocalMidnightAcrossTheDateLine() {
        val auckland = TimeZone.of("Pacific/Auckland")
        // 11:30 UTC on 18 October is already 19 October in Auckland.
        val bounds = DateFilter.Today.bounds(Instant.parse("2026-10-18T11:30:00Z"), auckland)
        assertEquals(Instant.parse("2026-10-18T11:00:00Z"), bounds.after)
    }

    @Test
    fun waitingForReplyKeepsMailFromTheUser() {
        val mine = message("1", "Ada@Example.org")
        val theirs = message("2", "bo@example.org")
        assertEquals(listOf(mine), listOf(mine, theirs).filter { it.fromAny(setOf("ada@example.org")) })
    }

    private fun message(
        id: String,
        from: String,
    ) = MailMessage(id, MailSender("", from), "", "", ReceivedAt("2026-10-18", "10:00"), MailLocation("Inbox", "Ada"))
}
