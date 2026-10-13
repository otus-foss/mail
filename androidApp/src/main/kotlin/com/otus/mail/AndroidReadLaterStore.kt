package com.otus.mail

import android.content.Context
import com.otus.mail.feature.folders.domain.ReadLaterStore

/** Each login's Read later folder id, stored under the login id. */
class AndroidReadLaterStore(
    context: Context,
) : ReadLaterStore {
    private val map = PreferenceMap(context, "otus_mail_read_later")

    override fun read(): Map<String, String> = map.read()

    override fun write(folders: Map<String, String>) = map.write(folders)
}
