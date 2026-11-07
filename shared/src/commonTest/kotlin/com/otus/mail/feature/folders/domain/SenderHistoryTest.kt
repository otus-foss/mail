package com.otus.mail.feature.folders.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SenderHistoryTest {
    @Test
    fun theEligibleFolderHoldingMostOfTheSendersMailIsSuggested() {
        val history = listOf(setOf("inbox"), setOf("receipts"), setOf("clients"), setOf("receipts", "inbox"))
        assertEquals("receipts", suggestedFolder(history, eligible = setOf("receipts", "clients")))
    }

    @Test
    fun aTieGoesToTheFolderOfTheNewerMessage() {
        val history = listOf(setOf("clients"), setOf("receipts"), setOf("receipts"), setOf("clients"))
        assertEquals("clients", suggestedFolder(history, eligible = setOf("receipts", "clients")))
    }

    @Test
    fun foldersThatAreNotEligibleNeverCount() {
        val history = listOf(setOf("inbox"), setOf("inbox"), setOf("trash"))
        assertNull(suggestedFolder(history, eligible = setOf("receipts")))
        assertNull(suggestedFolder(emptyList(), eligible = setOf("receipts")))
    }
}
