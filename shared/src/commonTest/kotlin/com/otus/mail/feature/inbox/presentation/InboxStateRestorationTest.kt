package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.saveable.SaverScope
import com.otus.mail.feature.inbox.domain.InboxPill
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.domain.MailboxScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class InboxStateRestorationTest {
    private val saveScope =
        object : SaverScope {
            override fun canBeSaved(value: Any) = true
        }

    @Test
    fun restoringSearchKeepsTheAccountMailboxAndFolderPillIdentities() {
        val search =
            InboxSearch(
                query = "saved query",
                accounts = setOf("ada"),
                mailboxes = setOf(MailboxScope("ada", "Receipts")),
            )
        val pill = InboxPill.Folder("ada", "r")
        val state = InboxState(true, search, folderPill = pill)
        val saver = InboxState.saver()
        val saved = with(saver) { saveScope.save(state) }
        val restored = assertNotNull(saver.restore(assertNotNull(saved)))
        assertEquals(search, restored.search)
        assertEquals(pill, restored.folderPill)
        assertEquals(emptySet(), restored.matching.mailboxes)
        restored.closeSearch()
        assertFalse(restored.searching)
        assertEquals(InboxSearch(), restored.search)
        assertEquals(pill, restored.folderPill)
    }

    @Test
    fun aFolderPageRestoresItsOwnDefaultInsteadOfTheInbox() {
        val default = InboxSearch(mailboxes = emptySet())
        val state = InboxState(true, default.copy(query = "invoices"), default)
        val saver = InboxState.saver(default)
        val saved = with(saver) { saveScope.save(state) }
        val restored = assertNotNull(saver.restore(assertNotNull(saved)))
        restored.reset()
        assertEquals(default, restored.matching)
    }
}
