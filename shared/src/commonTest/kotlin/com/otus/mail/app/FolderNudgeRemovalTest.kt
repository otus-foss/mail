package com.otus.mail.app

import com.otus.mail.core.storage.DeviceStore
import com.otus.mail.feature.folders.domain.FolderKey
import com.otus.mail.feature.folders.domain.FolderNudgeStore
import com.otus.mail.feature.folders.domain.FolderNudges
import com.otus.mail.testing.MemoryMapStore
import kotlin.test.Test
import kotlin.test.assertEquals

class FolderNudgeRemovalTest {
    // Login ids hold a space and the session URL's colons.
    private val ada = "ada https://mail.example.org/jmap/session"
    private val bob = "bob https://mail.example.com/jmap/session"

    @Test
    fun removingAnAccountForgetsOnlyItsNudges() {
        val store = DeviceStore(MemoryMapStore(), FolderNudgeStore)
        store.write(FolderNudges(setOf(FolderKey(ada, "M1"), FolderKey(bob, "M1"))).byLogin())

        MailDeviceStores(FolderStores(nudges = store)).forgetAccount(ada)

        assertEquals(FolderNudges(setOf(FolderKey(bob, "M1"))), FolderNudges.of(store.read()))
    }
}
