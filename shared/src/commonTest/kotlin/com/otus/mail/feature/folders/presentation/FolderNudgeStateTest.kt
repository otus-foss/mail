package com.otus.mail.feature.folders.presentation

import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.feature.folders.domain.FolderKey
import com.otus.mail.feature.folders.domain.FolderNudgeStore
import com.otus.mail.feature.folders.domain.READ_LATER_NAME
import com.otus.mail.feature.folders.domain.key
import com.otus.mail.testing.MemoryMapStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FolderNudgeStateTest {
    private class MemoryStore :
        MemoryMapStore(),
        FolderNudgeStore

    private val trash = MailFolder("ada", "M0", "Trash", role = MailboxRole.Trash)
    private val clients = MailFolder("ada", "M1", "Clients")
    private val travel = MailFolder("ada", "M2", "Travel")
    private val readLater = MailFolder("ada", "M3", READ_LATER_NAME)
    private val receipts = MailFolder("ada", "M4", "Receipts")
    private val ada =
        AccountFolders("ada", "Ada", listOf(trash, clients, travel, readLater, receipts), readLaterId = "M3")
    private val colors = FolderColorsState(accounts = { listOf(ada) })

    private fun state(
        store: MemoryStore = MemoryStore(),
        enabled: () -> Boolean = { true },
    ) = FolderNudgeState(store, colors, { listOf(ada) }, enabled)

    private val visible =
        mapOf(
            trash.key to 9,
            readLater.key to 8,
            clients.key to 2,
            travel.key to 5,
        )

    @Test
    fun theOwnUncoloredFolderWithTheMostConversationsIsPicked() {
        assertEquals(NudgeCandidate(travel, 5), state().pick(visible))
    }

    @Test
    fun coloredFoldersAndFoldersWithoutVisibleMailAreLeftOut() {
        colors.setColor(listOf(travel), "Blue")
        assertEquals(NudgeCandidate(clients, 2), state().pick(visible))
        assertNull(state().pick(mapOf(receipts.key to 0, trash.key to 4)))
    }

    @Test
    fun eitherSwitchOffShowsNoNudge() {
        var on = true
        val state = state { on }
        on = false
        assertNull(state.pick(visible))
        on = true
        assertEquals(travel, state.pick(visible)?.folder)
    }

    @Test
    fun theShownFolderStaysUntilColoredAndIsNeverOfferedAgain() {
        val store = MemoryStore()
        val state = state(store)
        state.markShown(travel.key)
        assertEquals(travel, state.pick(visible + (clients.key to 20))?.folder)

        colors.setColor(listOf(travel), "Teal")
        assertNull(state.pick(visible))
        assertEquals(mapOf("ada" to "M2"), store.saved)
        colors.setColor(listOf(travel), null)
        assertEquals(clients, state(store).pick(visible)?.folder)
    }

    @Test
    fun closingEndsTheNudgeForThisLaunchAndTheFolderForGood() {
        val store = MemoryStore()
        val state = state(store)
        state.markShown(travel.key)
        state.dismiss(travel.key)
        assertNull(state.pick(visible))
        assertEquals(clients, state(store).pick(visible)?.folder)
        assertEquals(setOf(FolderKey("ada", "M2")), state(store).nudges.folders)
    }
}
