package com.otus.mail.core.settings.presentation

import com.otus.mail.core.messages.domain.MailAction
import com.otus.mail.core.settings.domain.ActionBehavior
import com.otus.mail.core.settings.domain.MailBehavior
import com.otus.mail.core.settings.domain.MailBehaviorStore
import com.otus.mail.core.settings.domain.ReadBehavior
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
        val store = RecordingStore(MailBehavior(actions = ActionBehavior(endToStart = MailAction.Trash)))
        val state = MailBehaviorState(store)
        assertEquals(MailAction.Trash, state.behavior.actions.endToStart)

        state.updateActions { it.copy(startToEnd = MailAction.Flag) }
        state.updateRead { it.copy(markReadOnOpen = false) }
        assertEquals(
            MailBehavior(
                actions = ActionBehavior(MailAction.Flag, MailAction.Trash),
                read = ReadBehavior(markReadOnOpen = false),
            ),
            store.saved,
        )
        assertEquals(store.saved, state.behavior)
    }

    @Test
    fun unknownStoredSwipesAreNotGuessed() {
        assertEquals(MailAction.Archive, MailAction.fromStorageValue("archive"))
        assertNull(MailAction.fromStorageValue("snooze"))
        assertNull(MailAction.fromStorageValue(null))
    }
}
