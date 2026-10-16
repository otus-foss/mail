package com.otus.mail

import android.content.Context
import com.otus.push.PushStateStore

/** Each login's push record, stored under the login id. */
class AndroidPushStateStore(
    context: Context,
) : PushStateStore {
    private val map = PreferenceMap(context, "otus_mail_push")

    override fun read(): Map<String, String> = map.read()

    override fun write(records: Map<String, String>) = map.write(records)
}
