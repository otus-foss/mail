package com.otusfoss.mail.feature.folders.domain

import com.otusfoss.mail.core.mailbox.FolderKey
import com.otusfoss.mail.core.storage.DeviceStore
import com.otusfoss.mail.testing.MemoryMapStore
import kotlin.test.Test
import kotlin.test.assertEquals

class FolderNudgesTest {
    // Login ids hold a space and the session URL's colons.
    private val ada = "ada https://mail.example.org/jmap/session"
    private val bob = "bob https://mail.example.com/jmap/session"

    @Test
    fun nudgesSurviveTheStore() {
        val map = MemoryMapStore()
        val store = DeviceStore(map, FolderNudgeStore)
        val saved = FolderNudges().with(FolderKey(ada, "M1")).with(FolderKey(ada, "M2")).with(FolderKey(bob, "M1"))
        store.write(saved.byLogin())
        assertEquals(mapOf(ada to "M1\nM2", bob to "M1"), map.saved)
        assertEquals(saved, FolderNudges.of(store.read()))
    }

    @Test
    fun blankLinesAreSkipped() {
        val store = DeviceStore(MemoryMapStore(mapOf(ada to "M1\n\n", bob to "")), FolderNudgeStore)
        assertEquals(FolderNudges(setOf(FolderKey(ada, "M1"))), FolderNudges.of(store.read()))
    }
}
