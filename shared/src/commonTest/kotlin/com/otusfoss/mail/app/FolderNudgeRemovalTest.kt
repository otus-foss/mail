package com.otusfoss.mail.app

import com.otusfoss.mail.core.mailbox.FolderKey
import com.otusfoss.mail.core.storage.DeviceStore
import com.otusfoss.mail.feature.folders.domain.FolderNudgeStore
import com.otusfoss.mail.feature.folders.domain.FolderNudges
import com.otusfoss.mail.testing.MemoryMapStore
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
