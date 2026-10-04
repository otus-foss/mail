package com.otus.mail.core.dateformat

/** App-local preferences. Installed Mail and Calendar apps do not share this storage. */
interface DateFormatPreferenceStore {
    fun read(): DateFormatPreference

    fun write(preference: DateFormatPreference)
}
