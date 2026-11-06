package com.otus.mail.core.settings.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.core.settings.domain.MailBehavior
import com.otus.mail.core.settings.domain.MailBehaviorStore

/** The saved inbox behavior. Changes are written to [store] before they are shown. */
class MailBehaviorState(
    private val store: MailBehaviorStore? = null,
    initial: MailBehavior = store?.read() ?: MailBehavior(),
) {
    var behavior by mutableStateOf(initial)
        private set

    fun update(change: (MailBehavior) -> MailBehavior) {
        val next = change(behavior)
        store?.write(next)
        behavior = next
    }
}
