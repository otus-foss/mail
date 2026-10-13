package com.otus.mail.feature.notifications.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.feature.notifications.domain.InstantPush
import com.otus.mail.feature.notifications.domain.PushDistributor
import com.otus.push.PushStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** The Notifications page: which UnifiedPush app delivers new mail, and each account's status. */
class NotificationSettingsState(
    private val push: InstantPush,
    private val scope: CoroutineScope,
) {
    var distributors: List<PushDistributor> by mutableStateOf(emptyList())
        private set
    var current: PushDistributor? by mutableStateOf(null)
        private set

    /** Keyed by login id. */
    var statuses: Map<String, PushStatus> by mutableStateOf(emptyMap())
        private set

    init {
        scope.launch { load() }
    }

    fun choose(distributor: PushDistributor?) {
        current = distributor
        scope.launch {
            push.use(distributor)
            load()
        }
    }

    /** Reloads while the page is shown, so accounts move from connecting to instant on their own. */
    suspend fun watch() {
        while (true) {
            load()
            delay(if (PushStatus.Waiting in statuses.values) 2.seconds else 15.seconds)
        }
    }

    private suspend fun load() {
        distributors = push.distributors()
        current = push.current()
        statuses = push.statuses()
    }
}
