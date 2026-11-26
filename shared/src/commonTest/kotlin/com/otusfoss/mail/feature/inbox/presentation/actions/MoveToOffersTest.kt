package com.otusfoss.mail.feature.inbox.presentation.actions

import com.otusfoss.mail.core.messages.domain.BarButton.MoveTo
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.presentation.MoveTarget
import com.otusfoss.mail.core.messages.presentation.actions.ActionSubject
import com.otusfoss.mail.core.messages.presentation.actions.MoveOptions
import com.otusfoss.mail.core.messages.presentation.actions.label
import com.otusfoss.mail.core.messages.presentation.actions.offer
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
    ) = ActionSubject(actions, messages).offer(button)

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
        assertNull(ActionSubject(actions, messages).offer(MailAction.SuggestedMove))
        val suggested = ActionSubject(actions, messages, moves = MoveOptions(suggestion = targets.getValue("ada")[1]))
        val offered = assertNotNull(suggested.offer(MailAction.SuggestedMove))
        assertEquals("Move to Acme", offered.label)
        assertEquals("Acme", offered.primaryLabel)
        offered.run()
        assertEquals(listOf("1" to "ada-a"), moved)
    }

    @Test
    fun suggestedMoveLeavesMailOfAnotherAccountOutOfThatAccountsFolderWithTheSameId() {
        val sharedIdTargets =
            mapOf(
                "ada" to listOf(MoveTarget("f", listOf("Receipts"), own = true, path = listOf("Receipts"))),
                "grace" to listOf(MoveTarget("f", listOf("Family"), own = true, path = listOf("Family"))),
            )
        val movedNames = mutableListOf<Pair<String, String>>()
        val multiActions =
            offerActions(
                targets = { sharedIdTargets[it.location.accountId].orEmpty() },
                onMove = { message, target -> movedNames += message.id to target.name },
            )
        val suggestion = sharedIdTargets.getValue("ada").single()
        val offered =
            assertNotNull(
                ActionSubject(multiActions, fromBoth, moves = MoveOptions(suggestion = suggestion))
                    .offer(MailAction.SuggestedMove),
            )
        assertEquals("Move to Receipts", offered.label)
        offered.run()
        assertEquals(listOf("1" to "Receipts"), movedNames)
    }

    @Test
    fun suggestedMoveUsesTheTargetsAccountWhenNamed() {
        val sharedIdTargets =
            mapOf(
                "ada" to listOf(MoveTarget("f", listOf("Receipts"), own = true, accountId = "ada")),
                "grace" to listOf(MoveTarget("f", listOf("Family"), own = true, accountId = "grace")),
            )
        val movedNames = mutableListOf<Pair<String, String>>()
        val multiActions =
            offerActions(
                targets = { sharedIdTargets[it.location.accountId].orEmpty() },
                onMove = { message, target -> movedNames += message.id to target.name },
            )
        val suggestion = sharedIdTargets.getValue("grace").single()
        val offered =
            assertNotNull(
                ActionSubject(multiActions, fromBoth, moves = MoveOptions(suggestion = suggestion))
                    .offer(MailAction.SuggestedMove),
            )
        offered.run()
        assertEquals(listOf("2" to "Family"), movedNames)
    }

    private val acme = MoveTo(listOf("Clients", "Acme"))

    /** One message in each account. */
    private val fromBoth = listOf(offerMessage("1", "ada"), offerMessage("2", "grace"))
}
