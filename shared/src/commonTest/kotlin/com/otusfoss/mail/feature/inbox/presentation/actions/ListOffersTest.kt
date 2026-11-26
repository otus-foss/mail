package com.otusfoss.mail.feature.inbox.presentation.actions

import com.otusfoss.mail.core.messages.domain.BarButton
import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.core.messages.domain.ListFilter
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.QuickFacet
import com.otusfoss.mail.core.messages.presentation.actions.ActionSubject
import com.otusfoss.mail.core.messages.presentation.actions.ListExtras
import com.otusfoss.mail.core.messages.presentation.actions.ListFilters
import com.otusfoss.mail.core.messages.presentation.actions.ListSubject
import com.otusfoss.mail.core.messages.presentation.actions.offer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** What only the list's bar does: filter buttons, Select all, Go to a folder, and Write to. */
class ListOffersTest {
    private val changes = mutableListOf<String>()
    private val composed = mutableListOf<ComposeRequest>()
    private val unread = BarButton.Filter(ListFilter.Unread)
    private val flagged = BarButton.Filter(ListFilter.Quick(QuickFacet.Flagged))

    private fun list(
        on: Set<ListFilter> = emptySet(),
        results: List<MailMessage> = listOf(offerMessage("1")),
    ) = ActionSubject(
        offerActions(),
        list =
            ListSubject(
                results,
                accountId = "ada",
                onSearch = {},
                onFilters = {},
                onCompose = { composed += it },
                extras =
                    ListExtras(
                        ListFilters({ it in on }) { filter, set -> changes += "${filter.storageValue} $set" },
                        onSelectAll = { changes += "select all" },
                        folderPage = { key -> { changes += "open $key" }.takeIf { key == listOf("receipts") } },
                    ),
            ),
    )

    private val plain =
        ActionSubject(offerActions(), list = ListSubject(emptyList(), null, onSearch = {}, onFilters = {}))

    @Test
    fun aFilterButtonAppliesItsFilterAndThenClearsIt() {
        val off = assertNotNull(list().offer(unread))
        assertEquals("Apply unread filter", off.label)
        off.run()
        val on = assertNotNull(list(on = setOf(ListFilter.Unread)).offer(unread))
        assertEquals("Clear unread filter", on.label)
        on.run()
        assertEquals(listOf("unread true", "unread false"), changes)
    }

    @Test
    fun eachFilterButtonChangesOnlyItsOwnFilter() {
        assertNotNull(list(on = setOf(ListFilter.Unread)).offer(flagged)).run()
        assertEquals(listOf("flagged true"), changes)
    }

    @Test
    fun aListThatCannotTakeAnExtraOffersNone() {
        assertNull(plain.offer(unread))
        assertNull(plain.offer(MailAction.SelectAll))
        assertNull(plain.offer(BarButton.GoTo(listOf("Receipts"))))
    }

    @Test
    fun selectAllNeedsAMessageToSelect() {
        assertNull(list(results = emptyList()).offer(MailAction.SelectAll))
        assertNotNull(list().offer(MailAction.SelectAll)).run()
        assertEquals(listOf("select all"), changes)
    }

    @Test
    fun goToOpensTheFolderWhereAnAccountHasIt() {
        val offered = assertNotNull(list().offer(BarButton.GoTo(listOf("Receipts"))))
        assertEquals("Go to Receipts" to "Receipts", offered.label to offered.primaryLabel)
        offered.run()
        assertEquals(listOf("open [receipts]"), changes)
        assertNull(list().offer(BarButton.GoTo(listOf("Travel"))))
    }

    @Test
    fun writeToStartsADraftToItsAddressesFromTheListsAccount() {
        val offered = assertNotNull(list().offer(BarButton.WriteTo(listOf("mum@example.org", "dad@example.org"))))
        assertEquals("Write to mum@example.org and 1 more", offered.label)
        offered.run()
        val draft = composed.single() as ComposeRequest.New
        assertEquals("ada", draft.accountId)
        assertEquals(listOf("mum@example.org", "dad@example.org"), draft.to.map { it.address })
    }
}
