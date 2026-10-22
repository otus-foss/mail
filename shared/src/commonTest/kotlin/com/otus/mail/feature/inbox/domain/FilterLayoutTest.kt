package com.otus.mail.feature.inbox.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class FilterLayoutTest {
    @Test
    fun layoutsSurviveSavingAndSkipUnknownValues() {
        val layout = FilterLayout(listOf(FilterSection.Rows, FilterSection.Status), listOf(QuickFacet.Forwarded))
        assertEquals(layout, FilterLayout.decode(FilterLayout.encode(layout)))
        assertEquals("rows,status|forwarded", FilterLayout.encode(layout))
        assertEquals(FilterLayout(listOf(FilterSection.Sort), emptyList()), FilterLayout.decode("sort,later|"))
        assertEquals(FilterLayout(), FilterLayout.decode("garbage"))
    }

    @Test
    fun theDefaultShowsEverySectionAndQuickFilterInOrder() {
        val layout = FilterLayout()
        assertEquals(
            listOf(
                FilterSection.Status,
                FilterSection.Quick,
                FilterSection.People,
                FilterSection.Rows,
                FilterSection.Sort,
            ),
            layout.sections,
        )
        assertEquals(
            listOf(
                QuickFacet.Flagged,
                QuickFacet.ReadLater,
                QuickFacet.Attachments,
                QuickFacet.NeedsMyReply,
                QuickFacet.AwaitingReply,
                QuickFacet.Conversations,
                QuickFacet.Forwarded,
            ),
            layout.quick,
        )
    }

    @Test
    fun anEarlierLayoutKeepsItsOrderInTheNewSections() {
        val sections = "date,show,from,sent_to,skip_folders,sort"
        val show = "unread,conversations,waiting,not_flagged,newsletters,flagged"
        val layout = FilterLayout.decode("$sections|$show")
        assertEquals(
            listOf(
                FilterSection.Rows,
                FilterSection.Status,
                FilterSection.Quick,
                FilterSection.People,
                FilterSection.Sort,
            ),
            layout.sections,
        )
        assertEquals(
            listOf(QuickFacet.Conversations, QuickFacet.AwaitingReply, QuickFacet.Flagged, QuickFacet.NeedsMyReply),
            layout.quick,
            "Waiting for reply is Awaiting reply; Needs my reply is new, so it is added",
        )
    }

    @Test
    fun anEarlierLayoutWithoutShowLeavesTheStatusOut() {
        val layout = FilterLayout.decode("in_folder,size|read")
        assertEquals(listOf(FilterSection.Rows), layout.sections)
        assertEquals(listOf(QuickFacet.NeedsMyReply), layout.quick)
    }

    @Test
    fun aNewLayoutAddsNothing() {
        assertEquals(
            FilterLayout(listOf(FilterSection.Status), listOf(QuickFacet.Flagged)),
            FilterLayout.decode("status|flagged"),
        )
    }
}
