package com.otus.mail.app

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Says that the server's mail changed, as a push reports, so open screens reload without a pull.
 * Changes that come close together may arrive as one.
 */
class ServerChanges {
    private val pushed = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val changes: Flow<Unit> = pushed

    fun changed() {
        pushed.tryEmit(Unit)
    }
}
