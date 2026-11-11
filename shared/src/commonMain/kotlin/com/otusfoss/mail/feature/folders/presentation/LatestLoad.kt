package com.otusfoss.mail.feature.folders.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs a state holder's loads on [scope]. Starting a load cancels the one before it, but a
 * cancelled load can still finish after its replacement started, such as once a request it already
 * sent returns. So a load may show what it read, and end loading, only while it is the newest.
 */
internal class LatestLoad(
    private val scope: CoroutineScope,
) {
    private var job: Job? = null
    private var generation = 0

    /** Cancels the running load and starts [load]; its `isLatest` is true until another load starts. */
    fun start(load: suspend (isLatest: () -> Boolean) -> Unit) {
        job?.cancel()
        val started = ++generation
        job = scope.launch { load { started == generation } }
    }

    /** Cancels the running load and keeps it from showing what it read, as when the list changes locally. */
    fun invalidate() {
        job?.cancel()
        job = null
        generation++
    }

    /** Waits until the newest load finishes, following every load that replaces it meanwhile. */
    suspend fun awaitLatest() {
        do {
            val current = job ?: return
            current.join()
        } while (current !== job)
    }
}
