package com.otus.mail

import com.otus.mail.feature.folders.domain.FolderColorStore

/** Folder colors kept in [saved], so a test can read what the app stored. */
internal class InMemoryFolderColorStore : FolderColorStore {
    var saved = emptyMap<String, String>()

    override fun read() = saved

    override fun write(values: Map<String, String>) {
        saved = values
    }
}
