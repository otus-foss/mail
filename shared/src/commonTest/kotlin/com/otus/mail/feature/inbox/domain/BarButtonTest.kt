package com.otus.mail.feature.inbox.domain

import com.otus.mail.feature.inbox.domain.BarButton.MoveTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

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
    fun anUnknownOrEmptyValueIsNoButton() {
        listOf("bogus", "move_to:", "move_to:Clients/", "move_to://").forEach {
            assertNull(BarButton.fromStorageValue(it), "Decoding $it")
        }
    }
}
