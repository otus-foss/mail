package com.otus.mail

import com.otus.mail.core.mailbox.FolderKey
import com.otus.mail.core.storage.DeviceStore
import com.otus.mail.feature.folders.domain.FolderColorStore
import com.otus.storage.StringMapStore

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
