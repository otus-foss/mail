package com.otusfoss.mail.app

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Whether Mail is on screen, as the platform reports it; until it reports, Mail counts as shown.
 * Pushed server changes reload the open pages only while it is; see [reloadWhileShown].
 */
class MailVisibility {
    private val visible = MutableStateFlow(true)

    internal val shown: StateFlow<Boolean> = visible.asStateFlow()

    /** Mail came on screen, as when its activity started. */
    fun show() {
        visible.value = true
    }

    /** Mail left the screen, as when its activity stopped. */
    fun hide() {
        visible.value = false
    }
}

/**
 * Runs [reload] for each of [changes] while [shown], and once when Mail shows again after changes
 * came while it was hidden, so a push in the background loads nothing.
 */
internal suspend fun reloadWhileShown(
    changes: Flow<Unit>,
    shown: StateFlow<Boolean>,
    reload: () -> Unit,
) {
    coroutineScope {
        var missed = false
        launch {
            changes.collect {
                if (shown.value) reload() else missed = true
            }
        }
        shown.collect { visible ->
            if (visible && missed) {
                missed = false
                reload()
            }
        }
    }
}
