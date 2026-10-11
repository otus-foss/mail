package com.otus.mail

import android.content.Context
import androidx.core.content.edit
import com.otus.mail.feature.folders.domain.ReadLaterStore

/** Each login's Read later folder id, stored under the login id. */
class AndroidReadLaterStore(
    context: Context,
) : ReadLaterStore {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            "otus_mail_read_later",
            Context.MODE_PRIVATE,
        )

    override fun read(): Map<String, String> =
        preferences.all
            .mapNotNull { (login, folder) -> (folder as? String)?.let { login to it } }
            .toMap()

    override fun write(folders: Map<String, String>) {
        preferences.edit {
            clear()
            folders.forEach { (login, folder) -> putString(login, folder) }
        }
    }
}
