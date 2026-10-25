package com.otus.mail

import android.content.Context
import com.otus.mail.feature.contacts.domain.ContactStore

/** Contact changes waiting for the server, and the device's own contact choices. */
class AndroidContactStore(
    context: Context,
) : ContactStore {
    private val map = PreferenceMap(context, "otus_mail_contacts")

    override fun read(): Map<String, String> = map.read()

    override fun write(values: Map<String, String>) = map.write(values)
}
