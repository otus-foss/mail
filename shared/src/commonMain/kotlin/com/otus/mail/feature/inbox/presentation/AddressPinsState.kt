package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.feature.inbox.domain.AddressPinStore
import com.otus.mail.feature.inbox.domain.AddressPins
import com.otus.mail.feature.inbox.domain.MailScopeChoice
import com.otus.mail.feature.inbox.domain.readPins
import com.otus.mail.feature.inbox.domain.writePins

/** The pinned addresses of "Show mail from", written to [store] before they are shown. */
class AddressPinsState(
    private val store: AddressPinStore? = null,
) {
    var pins by mutableStateOf(store?.readPins() ?: AddressPins())
        private set

    /** Pins [address], or unpins it when it is pinned. */
    fun toggle(address: MailScopeChoice.Address) {
        // Start from the store, so this never writes back an account removed since the last read.
        val next = (store?.readPins() ?: pins).toggled(address)
        store?.writePins(next)
        pins = next
    }

    /** Reads the store again, as after an account is removed. */
    fun reload() {
        pins = store?.readPins() ?: pins
    }
}
