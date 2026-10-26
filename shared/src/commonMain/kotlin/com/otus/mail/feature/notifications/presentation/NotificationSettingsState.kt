package com.otus.mail.feature.notifications.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.feature.notifications.domain.InstantPush
import com.otus.mail.feature.notifications.domain.NotificationPreviewStore
import com.otus.mail.feature.notifications.domain.PreviewChoices
import com.otus.mail.feature.notifications.domain.PushDeliveryStatus
import com.otus.mail.feature.notifications.domain.PushDistributor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * The Notifications page: which service delivers new mail, each account's status, and what alerts
 * show, which [previewStore] keeps; null where alerts never show message content.
 */
class NotificationSettingsState(
    private val push: InstantPush,
    private val scope: CoroutineScope,
    private val previewStore: NotificationPreviewStore? = null,
) {
    val periodicChecks: Boolean get() = push.periodicChecks

    /** What alerts show, for all mail and per login and address; null where they never show message content. */
    var previews: PreviewChoices? by mutableStateOf(previewStore?.read())
        private set

    fun choosePreviews(change: (PreviewChoices) -> PreviewChoices) {
        val store = previewStore ?: return
        val chosen = change(previews ?: PreviewChoices())
        previews = chosen
        store.write(chosen)
    }

    var distributors: List<PushDistributor> by mutableStateOf(emptyList())
        private set
    var current: PushDistributor? by mutableStateOf(null)
        private set

    /** Keyed by login id. */
    var statuses: Map<String, PushDeliveryStatus> by mutableStateOf(emptyMap())
        private set

    /** Choices made so far, so a load that read the saved app before a choice cannot undo it. */
    private var choices = 0

    /** Choices still being applied; until they finish, the saved app may be the old one. */
    private var applying = 0

    init {
        scope.launch { load() }
    }

    fun choose(distributor: PushDistributor?) {
        current = distributor
        choices++
        applying++
        scope.launch {
            try {
                push.use(distributor)
            } finally {
                applying--
            }
            load()
        }
    }

    /** Reloads while the page is shown, so accounts move from connecting to instant on their own. */
    suspend fun watch() {
        while (true) {
            load()
            delay(if (PushDeliveryStatus.Connecting in statuses.values) 2.seconds else 15.seconds)
        }
    }

    private suspend fun load() {
        distributors = push.distributors()
        val choice = choices
        val saved = push.current()
        if (applying == 0 && choice == choices) current = saved
        statuses = push.statuses()
    }
}
