package com.otusfoss.mail

import com.otusfoss.core.storage.StringMapStore
import com.otusfoss.mail.core.mailbox.FolderKey
import com.otusfoss.mail.core.storage.DeviceStore
import com.otusfoss.mail.feature.folders.domain.FolderColorStore

/** Folder colors kept in memory, so a test can read what the app stored. */
internal fun inMemoryFolderColorStore(): DeviceStore<FolderKey, String> =
    DeviceStore(
        object : StringMapStore {
            private var saved = emptyMap<String, String>()

            override fun read() = saved

            override fun write(values: Map<String, String>) {
                saved = values
            }
        },
        FolderColorStore,
    )
