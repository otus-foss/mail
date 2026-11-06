package com.otus.mail.app

import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.LoginStore
import com.otus.mail.core.messages.domain.EmailChangeStore
import com.otus.mail.core.storage.DeviceStore
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.MemoryMapStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Marks and moves of a removed account, which must not reach the account if it is added again. */
class RemovedAccountChangesTest {
    @Test
    fun aChangeQueuedWhileTheAccountIsRemovedGoesWithIt() =
        runTest {
            val saved = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
            val changes = MemoryMapStore()
            val store =
                object : LoginStore by saved {
                    override fun remove(id: String) {
                        // A mark made offline after the removal began, before the login is deleted.
                        changes.write(mapOf(id to """{"m1": {"keywords/${'$'}seen": true}}"""))
                        saved.remove(id)
                    }
                }
            val waiting = WaitingStores(changes = DeviceStore(changes, EmailChangeStore))
            val services = MailServices(store, stores = MailDeviceStores(waiting = waiting))
            services.accounts.remove(ADA_LOGIN.id)
            assertEquals(emptyMap(), changes.saved)
        }
}
