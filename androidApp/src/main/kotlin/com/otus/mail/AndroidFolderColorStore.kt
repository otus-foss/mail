package com.otus.mail

import android.content.Context
import com.otus.mail.feature.folders.domain.FolderColorStore

/** Folder color names, stored under each folder's key. */
class AndroidFolderColorStore(
    context: Context,
) : FolderColorStore {
    private val map = PreferenceMap(context, "otus_mail_folder_colors")

    override fun read(): Map<String, String> = map.read()

    override fun write(names: Map<String, String>) = map.write(names)
}
