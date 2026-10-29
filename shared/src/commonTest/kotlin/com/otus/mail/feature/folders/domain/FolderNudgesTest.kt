package com.otus.mail.feature.folders.domain

import com.otus.mail.testing.MemoryMapStore
import kotlin.test.Test
import kotlin.test.assertEquals

class FolderNudgesTest {
    private class MemoryStore : MemoryMapStore(), FolderNudgeStore

    // Login ids hold a space and the session URL's colons.
    private val ada = "ada https://mail.example.org/jmap/session"
    private val bob = "bob https://mail.example.com/jmap/session"

    @Test
    fun nudgesSurviveTheStore() {
        val store = MemoryStore()
        val saved = FolderNudges().with(FolderKey(ada, "M1")).with(FolderKey(ada, "M2")).with(FolderKey(bob, "M1"))
        store.writeNudges(saved)
        assertEquals(mapOf(ada to "M1\nM2", bob to "M1"), store.saved)
        assertEquals(saved, store.readNudges())
    }

    @Test
    fun blankLinesAreSkipped() {
        val store = MemoryStore()
        store.saved = mapOf(ada to "M1\n\n", bob to "")
        assertEquals(FolderNudges(setOf(FolderKey(ada, "M1"))), store.readNudges())
    }

    @Test
    fun removingAnAccountForgetsOnlyItsNudges() {
        val store = MemoryStore()
        store.writeNudges(FolderNudges(setOf(FolderKey(ada, "M1"), FolderKey(bob, "M1"))))
        store.forgetAccount(ada)
        assertEquals(FolderNudges(setOf(FolderKey(bob, "M1"))), store.readNudges())
    }
}
