package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.MailBehaviorStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MailBehaviorStateTest {
    private class RecordingStore(
        var saved: MailBehavior,
    ) : MailBehaviorStore {
        override fun read() = saved

        override fun write(behavior: MailBehavior) {
            saved = behavior
        }
    }

    @Test
    fun startsFromTheSavedBehaviorAndSavesEachChange() {
        val store = RecordingStore(MailBehavior(endToStart = MailAction.Trash))
        val state = MailBehaviorState(store)
        assertEquals(MailAction.Trash, state.behavior.endToStart)

        state.update { it.copy(startToEnd = MailAction.Flag, markReadOnOpen = false) }
        assertEquals(MailBehavior(MailAction.Flag, MailAction.Trash, markReadOnOpen = false), store.saved)
        assertEquals(store.saved, state.behavior)
    }

    @Test
    fun unknownStoredSwipesAreNotGuessed() {
        assertEquals(MailAction.Archive, MailAction.fromStorageValue("archive"))
        assertNull(MailAction.fromStorageValue("snooze"))
        assertNull(MailAction.fromStorageValue(null))
    }
}
