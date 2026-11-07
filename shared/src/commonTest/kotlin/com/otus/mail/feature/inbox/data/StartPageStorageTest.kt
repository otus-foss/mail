package com.otus.mail.feature.inbox.data

import com.otus.mail.core.settings.data.KeyValueMailBehaviorStore
import com.otus.mail.core.settings.domain.MailBehavior
import com.otus.mail.core.settings.domain.SidebarBehavior
import com.otus.mail.core.settings.domain.SidebarPin
import com.otus.storage.KeyValueEditor
import com.otus.storage.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals

class StartPageStorageTest {
    private val values = MapValues()
    private val store = KeyValueMailBehaviorStore(values)

    @Test
    fun theInboxIsTheStartPageUntilTheUserChoosesAnother() {
        assertEquals(SidebarPin.Inbox, store.read().sidebar.startPage)
    }

    @Test
    fun aChosenFolderKeepsItsPathThroughSlashesInItsName() {
        val folder = SidebarPin.Folder(listOf("clients", "a/b testing"))
        store.write(MailBehavior(sidebar = SidebarBehavior(startPage = folder)))
        assertEquals(folder, store.read().sidebar.startPage)
    }

    @Test
    fun aValueThisVersionDoesNotKnowOpensTheInbox() {
        values.edit { put("start_page", "calendar") }
        assertEquals(SidebarPin.Inbox, store.read().sidebar.startPage)
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
