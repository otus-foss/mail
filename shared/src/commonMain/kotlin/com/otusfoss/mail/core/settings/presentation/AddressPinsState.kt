package com.otusfoss.mail.core.settings.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otusfoss.mail.core.messages.domain.MailScopeChoice
import com.otusfoss.mail.core.settings.domain.AddressPins
import com.otusfoss.mail.core.storage.DeviceStore

/** The pinned addresses of "Show mail from", written to [store] before they are shown. */
class AddressPinsState(
    private val store: DeviceStore<String, List<String>>? = null,
) {
    var pins by mutableStateOf(saved() ?: AddressPins())
        private set

    /** Pins [address], or unpins it when it is pinned. */
    fun toggle(address: MailScopeChoice.Address) {
        // Start from the store, so this never writes back an account removed since the last read.
        val next = (saved() ?: pins).toggled(address)
        store?.write(next.byLogin())
        pins = next
    }

    /** Reads the store again, as after an account is removed. */
    fun reload() {
        pins = saved() ?: pins
    }

    private fun saved(): AddressPins? = store?.read()?.let(AddressPins::of)
}
