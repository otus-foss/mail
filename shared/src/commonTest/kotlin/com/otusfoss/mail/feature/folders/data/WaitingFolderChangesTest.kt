package com.otusfoss.mail.feature.folders.data

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.core.mailbox.FolderFailure
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.core.storage.DeviceStore
import com.otusfoss.mail.feature.folders.domain.FolderChangeStore
import com.otusfoss.mail.feature.folders.domain.FolderOutcome
import com.otusfoss.mail.testing.ADA_LOGIN
import com.otusfoss.mail.testing.MemoryMapStore
import com.otusfoss.mail.testing.STALWART_SESSION
import com.otusfoss.mail.testing.stalwartLike
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Renames and moves of folders made offline wait on the device and go to the server in order. */
class WaitingFolderChangesTest {
    private val login = ADA_LOGIN
    private val logins = InMemoryLoginStore().apply { save(login, "secret") }
    private val cache = InMemoryMailCache()
    private val map = MemoryMapStore()
    private val sent = mutableListOf<String>()
    private var down = false
    private var refuseUpdates = false
    private var garbled = false
    private var woken = 0
    private lateinit var waiting: WaitingFolderChanges
    private val repository = repository()

    private fun repository(): JmapFolderRepository {
        val api = { body: String ->
            if (down) throw IOException("The network is down")
            sent += body
            when {
                "Mailbox/set" !in body -> MAILBOXES
                garbled -> "<html>Bad gateway</html>"
                else -> setAnswer()
            }
        }
        val connections = JmapConnections(JmapClient(stalwartLike(session = STALWART_SESSION, api = api)), logins)
        val mailboxes = Mailboxes(cache)
        val store = DeviceStore(map, FolderChangeStore)
        waiting = WaitingFolderChanges(connections, mailboxes, store, { woken++ }, Dispatchers.Unconfined)
        return JmapFolderRepository(connections, mailboxes, waiting)
    }

    private fun setAnswer() =
        if (refuseUpdates) {
            """{"methodResponses": [["Mailbox/set", {"newState": "m2",
               "notUpdated": {"r": {"type": "invalidProperties"}}}, "c0"]]}"""
        } else {
            """{"methodResponses": [["Mailbox/set", {"newState": "m2", "updated": {"r": null}}, "c0"]]}"""
        }

    private suspend fun receipts() = requireNotNull(repository.accounts().single().folder("r"))

    @Test
    fun anOfflineRenameCountsAsMadeShowsAtOnceAndWaits() =
        runTest {
            val folder = receipts()
            down = true

            assertEquals(FolderOutcome.Done("r"), repository.rename(folder, " Bills "))

            val offline = repository.accounts().single()
            assertEquals("Bills", offline.folder("r")?.name)
            assertEquals(FolderFailure.Mail(MailFailure.Unreachable), offline.failure)
            assertEquals(
                "Bills",
                repository
                    .cachedAccounts()
                    .single()
                    .folder("r")
                    ?.name,
            )
            assertEquals(1, woken)
            repository()
            assertTrue(waiting.flush(), "Another instance, as after a restart, still holds it")
        }

    @Test
    fun theNextLoadSendsWhatWaitsInOrderAndReadsEveryMailboxAgain() =
        runTest {
            val folder = receipts()
            down = true
            repository.rename(folder, "Bills")
            repository.rename(folder, "Invoices")
            down = false
            sent.clear()

            repository.accounts()

            assertTrue("\"update\":{\"r\":{\"name\":\"Bills\"}}" in sent[0], sent[0])
            assertTrue("\"update\":{\"r\":{\"name\":\"Invoices\"}}" in sent[1], sent[1])
            assertTrue("Mailbox/get" in sent[2] && "Mailbox/changes" !in sent[2], sent[2])
            assertFalse(waiting.flush())
            assertEquals(emptyMap(), map.read())
        }

    @Test
    fun aRefusedChangeIsDroppedAndTheServersFoldersShowAgain() =
        runTest {
            val folder = receipts()
            down = true
            repository.rename(folder, "Bills")
            down = false
            refuseUpdates = true

            val loaded = repository.accounts().single()

            assertEquals("Receipts", loaded.folder("r")?.name)
            assertFalse(waiting.flush())
        }

    @Test
    fun aCaptivePortalPageKeepsTheChangeWaiting() =
        runTest {
            val folder = receipts()
            down = true
            repository.rename(folder, "Bills")
            down = false
            garbled = true

            repository.accounts()
            garbled = false
            sent.clear()
            val loaded = repository.accounts().single()

            assertTrue("\"update\":{\"r\":{\"name\":\"Bills\"}}" in sent[0], sent[0])
            assertEquals(null, loaded.failure)
            assertEquals(emptyMap(), map.read())
        }

    @Test
    fun aChangeMadeOnlineGoesAfterTheOnesThatWait() =
        runTest {
            val folder = receipts()
            down = true
            repository.rename(folder, "Bills")
            down = false
            sent.clear()

            assertEquals(FolderOutcome.Done("r"), repository.rename(folder, "Invoices"))

            val names = sent.map { it.substringAfter("\"name\":\"").substringBefore('"') }
            assertEquals(listOf("Bills", "Invoices"), names)
        }

    @Test
    fun aRefusalOnlineIsNotKept() =
        runTest {
            val folder = receipts()
            refuseUpdates = true

            assertEquals(FolderOutcome.Failed(FolderFailure.Invalid), repository.rename(folder, "Bills"))
            assertFalse(waiting.flush())
        }

    @Test
    fun removingTheAccountDropsItsWaitingChanges() =
        runTest {
            val folder = receipts()
            down = true
            repository.rename(folder, "Bills")

            waiting.removing(login.id)

            assertEquals(emptyMap(), map.read())
        }

    private companion object {
        const val MAILBOXES =
            """{"methodResponses": [["Mailbox/get", {"state": "m", "list": [
               {"id": "a", "name": "Inbox", "role": "inbox"}, {"id": "r", "name": "Receipts"}]}, "c0"]]}"""
    }
}
