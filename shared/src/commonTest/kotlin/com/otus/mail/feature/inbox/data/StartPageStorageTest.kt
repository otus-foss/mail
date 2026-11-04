package com.otus.mail.feature.inbox.data

import com.otus.mail.core.preferences.KeyValueEditor
import com.otus.mail.core.preferences.KeyValueStore
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.SidebarPin
import kotlin.test.Test
import kotlin.test.assertEquals

class StartPageStorageTest {
    private val values = MapValues()
    private val store = KeyValueMailBehaviorStore(values)

    @Test
    fun theInboxIsTheStartPageUntilTheUserChoosesAnother() {
        assertEquals(SidebarPin.Inbox, store.read().startPage)
    }

    @Test
    fun aChosenFolderKeepsItsPathThroughSlashesInItsName() {
        val folder = SidebarPin.Folder(listOf("clients", "a/b testing"))
        store.write(MailBehavior(startPage = folder))
        assertEquals(folder, store.read().startPage)
    }

    @Test
    fun aValueThisVersionDoesNotKnowOpensTheInbox() {
        values.edit { put("start_page", "calendar") }
        assertEquals(SidebarPin.Inbox, store.read().startPage)
    }

    private class MapValues : KeyValueStore {
        private val saved = mutableMapOf<String, Any>()

        override fun string(key: String): String? = saved[key] as? String

        override fun flag(key: String): Boolean? = saved[key] as? Boolean

        override fun int(key: String): Int? = saved[key] as? Int

        override fun edit(changes: KeyValueEditor.() -> Unit) {
            val editor =
                object : KeyValueEditor {
                    override fun put(
                        key: String,
                        value: String,
                    ) {
                        saved[key] = value
                    }

                    override fun put(
                        key: String,
                        value: Boolean,
                    ) {
                        saved[key] = value
                    }

                    override fun put(
                        key: String,
                        value: Int,
                    ) {
                        saved[key] = value
                    }
                }
            editor.changes()
        }
    }
}
