package com.otusfoss.mail.feature.folders.presentation

import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.core.mailbox.AccountFolders
import com.otusfoss.mail.core.mailbox.FolderFailure
import com.otusfoss.mail.core.mailbox.MailFolder
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.mailbox.sharedFolders
import com.otusfoss.mail.core.storage.DeviceStore
import com.otusfoss.mail.feature.folders.domain.FolderColorStore
import com.otusfoss.mail.feature.folders.domain.FolderOutcome
import com.otusfoss.mail.feature.folders.domain.FolderPlacement
import com.otusfoss.mail.feature.folders.domain.FolderRepository
import com.otusfoss.mail.feature.folders.domain.READ_LATER_NAME
import com.otusfoss.mail.testing.MemoryMapStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class FolderColorsStateTest {
    private class MemoryStore : MemoryMapStore() {
        val typed = DeviceStore(this, FolderColorStore)
    }

    /** Deletes every folder unless [refuse] is set. */
    private class Deletes(
        private val accounts: List<AccountFolders>,
        var refuse: Boolean = false,
    ) : FolderRepository {
        override suspend fun accounts() = accounts

        override suspend fun create(
            accountId: String,
            name: String,
            parentId: String?,
        ) = FolderOutcome.Done("M9")

        override suspend fun rename(
            folder: MailFolder,
            name: String,
        ) = FolderOutcome.Done(folder.id)

        override suspend fun place(placement: FolderPlacement) = FolderOutcome.Done(placement.folder.id)

        override suspend fun delete(folder: MailFolder) =
            if (refuse) {
                FolderOutcome.Failed(FolderFailure.Mail(MailFailure.NotPermitted))
            } else {
                FolderOutcome.Done(folder.id)
            }
    }

    private val clients = MailFolder("ada", "M1", "Clients")
    private val trash = MailFolder("ada", "M0", "Trash", role = MailboxRole.Trash)
    private val readLater = MailFolder("ada", "M2", READ_LATER_NAME)
    private val ada = AccountFolders("ada", "Ada", listOf(trash, clients, readLater), readLaterId = "M2")

    @Test
    fun theSwitchHidesColorsWithoutForgettingThem() {
        val store = MemoryStore()
        var shown = true
        val state = FolderColorsState(store.typed, { listOf(ada) }) { shown }
        state.setColor(listOf(clients), "Orange")
        val group = sharedFolders(listOf(ada)).single()
        assertEquals("Orange", state.colorOf(clients))
        assertEquals("Orange", state.groupColorOf(group))

        shown = false
        assertNull(state.colorOf(clients))
        assertNull(state.groupColorOf(group))
        assertEquals("Orange", FolderColorsState(store.typed, { listOf(ada) }).colorOf(clients))
    }

    @Test
    fun roleFoldersAndReadLaterStayUncolored() {
        val store = MemoryStore()
        val state = FolderColorsState(store.typed, { listOf(ada) })
        state.setColor(listOf(trash, readLater, clients), "Red")
        assertNull(state.colorOf(trash))
        assertNull(state.colorOf(readLater))
        assertEquals("Red", state.colorOf(clients))
        assertEquals(1, store.saved.size)
    }

    @Test
    fun choosingTheDefaultForgetsTheColor() {
        val store = MemoryStore()
        val state = FolderColorsState(store.typed, { listOf(ada) })
        state.setColor(listOf(clients), "Teal")
        state.setColor(listOf(clients), null)
        assertNull(state.colorOf(clients))
        assertEquals(emptyMap(), store.saved)
    }

    @Test
    fun aChangeAfterAnAccountIsRemovedDoesNotBringBackItsColors() {
        val store = MemoryStore()
        val bobClients = MailFolder("bob", "M1", "Clients")
        val state = FolderColorsState(store.typed)
        state.setColor(listOf(bobClients), "Blue")
        store.saved = emptyMap()
        state.setColor(listOf(clients), "Red")
        assertNull(state.colorOf(bobClients))
        assertEquals(1, store.saved.size)
    }

    @Test
    fun deletingAFolderForgetsItsColorOnlyOnceTheServerDeletesIt() =
        runTest {
            val store = MemoryStore()
            lateinit var colors: FolderColorsState
            val repository = Deletes(listOf(ada), refuse = true)
            val folders = FoldersState(repository, this, onDeleted = { colors.forget(it) }) { repository.accounts() }
            colors = FolderColorsState(store.typed, { folders.accounts })
            colors.setColor(listOf(clients), "Pink")

            folders.delete(clients)
            runCurrent()
            assertEquals("Pink", colors.colorOf(clients))

            repository.refuse = false
            folders.delete(clients)
            runCurrent()
            assertNull(colors.colorOf(clients))
            assertEquals(emptyMap(), store.saved)
        }
}
