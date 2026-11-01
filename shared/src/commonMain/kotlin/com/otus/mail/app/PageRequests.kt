package com.otus.mail.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Pages that work outliving the activity asks for, such as a draft that a toast's Undo opens again.
 * Such work cannot write the page of the composition that started it, which a recreated activity,
 * as after a rotation or a theme, language, or window size change, has replaced. The [App] on
 * screen [collect]s the requests instead. One per process; [work] is the process's scope.
 */
internal class PageRequests(
    private val work: CoroutineScope,
) {
    private val waiting = MutableStateFlow<List<PageRequest>>(emptyList())

    /**
     * Shows [page] and returns once an [App] has taken it, which may be after the activity comes
     * back. Whatever the page replaces, such as a message in the outbox, may go only then.
     */
    suspend fun show(page: MailPage) {
        val request = PageRequest(page)
        waiting.update { it + request }
        request.taken.await()
    }

    /** [show] for a toast's action, which cannot wait. */
    fun showLater(page: MailPage) {
        work.launch { show(page) }
    }

    /** Hands each requested page to [show], for as long as the caller's composition runs. */
    suspend fun collect(show: (MailPage) -> Unit) {
        waiting.collect { requests ->
            // Taken without suspending, so a composition that leaves meanwhile leaves them to the next.
            requests.forEach { request ->
                show(request.page)
                request.taken.complete(Unit)
            }
            waiting.update { it - requests.toSet() }
        }
    }
}

private class PageRequest(
    val page: MailPage,
) {
    val taken = CompletableDeferred<Unit>()
}
