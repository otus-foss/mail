package com.otus.mail

import android.content.Context
import com.otus.mail.feature.folders.domain.FolderNudgeStore

/** Each login's nudged folder ids, stored under the login id. */
class AndroidFolderNudgeStore(
    context: Context,
) : FolderNudgeStore {
    private val map = PreferenceMap(context, "otus_mail_folder_nudges")

    override fun read(): Map<String, String> = map.read()

    override fun write(nudges: Map<String, String>) = map.write(nudges)
}
