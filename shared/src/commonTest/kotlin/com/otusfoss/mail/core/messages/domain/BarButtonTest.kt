package com.otusfoss.mail.core.messages.domain

import com.otusfoss.mail.core.messages.domain.BarButton.MoveTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BarButtonTest {
    @Test
    fun aMoveToButtonIsSavedByItsFolderPath() {
        val acme = MoveTo(listOf("Clients", "Acme"))
        assertEquals("move_to:Clients/Acme", acme.storageValue)
        assertEquals(acme, BarButton.fromStorageValue(acme.storageValue))
    }

    @Test
    fun separatorsInAFolderNameSurviveSaving() {
        val odd = MoveTo(listOf("A/B, C|D", "100%"))
        val saved = odd.storageValue
        assertEquals("move_to:A%2FB%2C C%7CD/100%25", saved)
        assertEquals(odd.path, (BarButton.fromStorageValue(saved) as MoveTo).path)
    }

    @Test
    fun pathsThatDifferOnlyInCaseAreOneButton() {
        assertEquals(MoveTo(listOf("Clients", "Acme")), MoveTo(listOf("clients", "ACME")))
        assertEquals(MoveTo(listOf("Clients")).hashCode(), MoveTo(listOf("clients")).hashCode())
        assertNotEquals(MoveTo(listOf("Clients")), MoveTo(listOf("Clients", "Acme")))
    }

    @Test
    fun plainActionsKeepTheirStorageValues() {
        MailAction.entries.forEach { assertEquals(it, BarButton.fromStorageValue(it.storageValue)) }
        assertEquals(MailAction.Move, BarButton.fromStorageValue("move"))
    }

    @Test
    fun everyKindOfButtonReadsBackAsSaved() {
        val steps = listOf(MailAction.Read, BarButton.LabelAs(listOf("A/B")), MailAction.Archive)
        val chain = BarButton.Chain("File, then go", steps)
        val buttons =
            listOf(
                BarButton.LabelAs(listOf("Clients", "Acme")),
                BarButton.GoTo(listOf("Travel")),
                BarButton.Filter(ListFilter.Unread),
                BarButton.Filter(ListFilter.Quick(QuickFacet.NeedsMyReply)),
                BarButton.Filter(ListFilter.Type(TypeFacet.Newsletters)),
                BarButton.WriteTo(listOf("mum@example.org", "dad@example.org")),
                chain,
            )
        buttons.forEach { assertEquals(it, BarButton.fromStorageValue(it.storageValue), "Decoding ${it.storageValue}") }
        assertEquals("chain:File%2C then go/read/label:A%252FB/archive", chain.storageValue)
    }

    @Test
    fun folderButtonsOfDifferentKindsAreDifferentButtons() {
        assertNotEquals<BarButton>(MoveTo(listOf("Receipts")), BarButton.LabelAs(listOf("Receipts")))
        assertEquals(BarButton.LabelAs(listOf("Receipts")), BarButton.LabelAs(listOf("RECEIPTS")))
    }

    @Test
    fun aChainMarksAndLabelsBeforeItFilesOnce() {
        val label = BarButton.LabelAs(listOf("Receipts"))
        assertTrue(listOf(MailAction.Read, MailAction.Flag, label, MoveTo(listOf("Done"))).isChain())
        assertTrue(listOf<BarButton>(MailAction.Trash).isChain())
        val broken =
            listOf(
                emptyList(),
                listOf(MailAction.Archive, MailAction.Read),
                listOf(MailAction.Archive, MailAction.Trash),
                listOf(MailAction.Read, MailAction.Read),
                listOf(MailAction.Read, MailAction.Reply),
            )
        broken.forEach { assertFalse(it.isChain(), "Steps $it") }
        assertNull(BarButton.fromStorageValue("chain:Bad/archive/read"))
        assertNull(BarButton.fromStorageValue("chain:Nested/chain:Inner%2Fread"))
    }

    @Test
    fun noneIsNoSwipe() {
        assertNull(BarButton.decodeSwipe("none", MailAction.Read))
        assertEquals("none", BarButton.encodeSwipe(null))
    }

    @Test
    fun aMissingUnknownOrUnswipeableValueReadsAsTheDefault() {
        listOf(null, "bogus", "search", "rule_for_sender").forEach {
            assertEquals(MailAction.Archive, BarButton.decodeSwipe(it, MailAction.Archive), "Decoding $it")
        }
    }

    @Test
    fun everySwipeableButtonReadsBackAsSaved() {
        (MailAction.entries.filter { it.swipeable } + MoveTo(listOf("Clients", "Acme"))).forEach {
            assertEquals(it, BarButton.decodeSwipe(BarButton.encodeSwipe(it), default = null))
        }
    }

    @Test
    fun anUnknownOrEmptyValueIsNoButton() {
        listOf("bogus", "move_to:", "move_to:Clients/", "move_to://").forEach {
            assertNull(BarButton.fromStorageValue(it), "Decoding $it")
        }
    }
}
