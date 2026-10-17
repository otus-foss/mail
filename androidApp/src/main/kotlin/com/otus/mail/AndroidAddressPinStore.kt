package com.otus.mail

import android.content.Context
import com.otus.mail.feature.inbox.domain.AddressPinStore

/** Each login's pinned addresses, stored under the login id. */
class AndroidAddressPinStore(
    context: Context,
) : AddressPinStore {
    private val map = PreferenceMap(context, "otus_mail_address_pins")

    override fun read(): Map<String, String> = map.read()

    override fun write(pins: Map<String, String>) = map.write(pins)
}
