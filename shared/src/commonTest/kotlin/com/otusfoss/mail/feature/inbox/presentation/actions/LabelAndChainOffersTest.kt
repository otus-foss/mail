package com.otusfoss.mail.feature.inbox.presentation.actions

import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.BarButton
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MessageFlag
import com.otusfoss.mail.core.messages.presentation.LabelChange
import com.otusfoss.mail.core.messages.presentation.MoveTarget
import com.otusfoss.mail.core.messages.presentation.actions.ActionSubject
import com.otusfoss.mail.core.messages.presentation.actions.offer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Label as adds a folder and keeps the others, and a chain runs its steps in order as one button. */
class LabelAndChainOffersTest {
    private val log = mutableListOf<String>()
    private val receipts = BarButton.LabelAs(listOf("Receipts"))

    private val folders =
        listOf(
            MoveTarget("receipts", listOf("Receipts"), own = true, path = listOf("Receipts")),
            MoveTarget("archive", listOf("Archive"), MailboxRole.Archive),
        )

    /** Messages 1 and 2 can take the label; message 2 has it already. */
    private val actions =
        offerActions(
            targets = { folders },
            onMove = { message, target -> log += "move ${message.id} to ${target.folderId}" },
            onMark = { messages, flag, on -> log += "$flag $on ${messages.map { it.id }}" },
            label = { message, folder ->
                val adds = message.id != "2"
                LabelChange(adds, leaves = false) { log += "label ${message.id} ${folder.folderId} $adds" }
            },
        )

    private fun subject(vararg messages: MailMessage) = ActionSubject(actions, messages.toList())

    @Test
    fun labelAsAddsTheFolderToMessagesThatLackIt() {
        val offered = assertNotNull(subject(offerMessage("1"), offerMessage("2")).offer(receipts))
        assertEquals("Label as Receipts" to false, offered.label to offered.leaves)
        offered.run()
        assertEquals(listOf("label 1 receipts true"), log)
    }

    @Test
    fun labelAsTakesTheFolderOffOnceEveryMessageHasIt() {
        val offered = assertNotNull(subject(offerMessage("2")).offer(receipts))
        assertEquals("Remove label Receipts", offered.label)
        offered.run()
        assertEquals(listOf("label 2 receipts false"), log)
        assertNull(subject(offerMessage("1")).offer(BarButton.LabelAs(listOf("Travel"))), "No folder at that path")
    }

    @Test
    fun aChainOnlyTurnsThingsOnAndFilesLast() {
        val chain = BarButton.Chain("File receipt", listOf(MailAction.Read, receipts, MailAction.Archive))
        val offered = assertNotNull(subject(offerMessage("1", unread = true), offerMessage("2")).offer(chain))
        assertEquals("File receipt", offered.label)
        assertTrue(offered.leaves)
        offered.run()
        assertEquals(
            listOf(
                "${MessageFlag.Read} true [1]",
                "label 1 receipts true",
                "move 1 to archive",
                "move 2 to archive",
            ),
            log,
        )
    }

    @Test
    fun aChainWithNothingLeftToDoIsNotOffered() {
        val chain = BarButton.Chain("Read and label", listOf(MailAction.Read, receipts))
        assertNull(subject(offerMessage("2")).offer(chain), "Read already and labeled already")
    }
}
