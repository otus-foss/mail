package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.MailBehaviorStore
import com.otus.mail.feature.inbox.domain.MessageSwipe
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
        val store = RecordingStore(MailBehavior(endToStart = MessageSwipe.Trash))
        val state = MailBehaviorState(store)
        assertEquals(MessageSwipe.Trash, state.behavior.endToStart)

        state.update { it.copy(startToEnd = MessageSwipe.Flag, markReadOnOpen = false) }
        assertEquals(MailBehavior(MessageSwipe.Flag, MessageSwipe.Trash, markReadOnOpen = false), store.saved)
        assertEquals(store.saved, state.behavior)
    }

    @Test
    fun unknownStoredSwipesAreNotGuessed() {
        assertEquals(MessageSwipe.Archive, MessageSwipe.fromStorageValue("archive"))
        assertNull(MessageSwipe.fromStorageValue("snooze"))
        assertNull(MessageSwipe.fromStorageValue(null))
    }
}
