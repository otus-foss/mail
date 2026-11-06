package com.otus.mail.feature.inbox.presentation.components

import com.otus.mail.feature.inbox.domain.BarButton.MoveTo
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.MoveTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoveToOffersTest {
    private val moved = mutableListOf<Pair<String, String>>()

    /** Ada's account has Receipts and Clients / Acme; Grace's has only RECEIPTS. */
    private val targets =
        mapOf(
            "ada" to
                listOf(
                    MoveTarget("ada-r", listOf("Receipts"), own = true, path = listOf("Receipts")),
                    MoveTarget(
                        "ada-a",
                        listOf("Clients", "Acme"),
                        name = "Acme",
                        own = true,
                        path = listOf("Clients", "Acme"),
                    ),
                    MoveTarget("ada-t", listOf("Trash")),
                ),
            "grace" to listOf(MoveTarget("grace-r", listOf("RECEIPTS"), own = true, path = listOf("RECEIPTS"))),
        )

    private val actions =
        offerActions(
            targets = { targets[it.location.accountId].orEmpty() },
            onMove = { message, target -> moved += message.id to target.folderId },
        )

    private fun offer(
        messages: List<MailMessage>,
        button: MoveTo,
    ) = actions.offer(messages, button, chooseFolder = { _, _ -> })

    @Test
    fun aMoveToButtonMovesEachMessageWithinItsOwnAccount() {
        val offered = assertNotNull(offer(fromBoth, MoveTo(listOf("receipts"))))
        assertEquals("Move to Receipts", offered.label)
        assertEquals("Receipts", offered.primaryLabel)
        assertTrue(offered.leaves)
        offered.run()
        assertEquals(listOf("1" to "ada-r", "2" to "grace-r"), moved)
    }

    @Test
    fun aMessageWhoseAccountLacksTheFolderIsSkipped() {
        val offered = assertNotNull(offer(fromBoth, acme))
        assertEquals("Acme", offered.primaryLabel)
        offered.run()
        assertEquals(listOf("1" to "ada-a"), moved)
    }

    @Test
    fun noFolderAtThePathDisablesTheButton() {
        assertNull(offer(listOf(offerMessage("2", "grace")), acme))
        assertNull(offer(listOf(offerMessage("1", "ada")), MoveTo(listOf("Travel"))))
        // System folders have no path, so a user's folder named Trash elsewhere does not match them.
        assertNull(offer(listOf(offerMessage("1", "ada")), MoveTo(listOf("Trash"))))
    }

    @Test
    fun suggestedMoveGoesToTheSuggestedFolderAndIsOffOtherwise() {
        val messages = listOf(offerMessage("1", "ada"))
        assertNull(actions.offer(messages, MailAction.SuggestedMove, { _, _ -> }))
        val offered =
            assertNotNull(actions.offer(messages, MailAction.SuggestedMove, { _, _ -> }, targets.getValue("ada")[1]))
        assertEquals("Move to Acme", offered.label)
        assertEquals("Acme", offered.primaryLabel)
        offered.run()
        assertEquals(listOf("1" to "ada-a"), moved)
    }

    private val acme = MoveTo(listOf("Clients", "Acme"))

    /** One message in each account. */
    private val fromBoth = listOf(offerMessage("1", "ada"), offerMessage("2", "grace"))
}
