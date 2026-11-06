package com.otus.mail.feature.contacts.presentation

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.core.storage.DeviceStore
import com.otus.mail.feature.contacts.domain.ContactStore
import com.otus.mail.feature.contacts.domain.FrequentScan
import com.otus.mail.feature.contacts.domain.MailTraceSource
import com.otus.mail.feature.contacts.domain.frequencyScores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * How much the user deals with each address, from each login's recent mail. The scores are kept on
 * the device per login, so the list shows them at once, and worked out again once they are a day
 * old, which costs a few small requests per login.
 */
internal class FrequentContacts(
    private val source: MailTraceSource,
    private val store: DeviceStore<String, String>?,
    private val scope: CoroutineScope,
    private val now: () -> Instant = { Clock.System.now() },
) {
    private var scans by mutableStateOf(store?.read()?.let(ContactStore.FREQUENT::of).orEmpty())
    private var scanning = false

    /** Every login's scores together, by lowercase address. */
    val scores: Map<String, Double> by derivedStateOf {
        scans.values
            .flatMap { it.scores.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, scores) -> scores.sum() }
    }

    /** Scans each login whose scores are missing or a day old, and forgets removed logins. */
    fun refresh() {
        if (scanning) return
        scanning = true
        scope.launch {
            try {
                val logins = source.logins()
                val fresh =
                    logins
                        .filter { id ->
                            val scan = scans[id]
                            scan == null || now().toEpochMilliseconds() - scan.atMillis >= MAX_AGE
                        }.mapNotNull { id ->
                            source.traces(id)?.let { traces ->
                                id to FrequentScan(now().toEpochMilliseconds(), frequencyScores(traces, now()))
                            }
                        }
                scans = (scans + fresh).filterKeys { it in logins }
                store?.update { it + ContactStore.FREQUENT.entry(scans) }
            } finally {
                scanning = false
            }
        }
    }

    companion object {
        private val MAX_AGE = 1.days.inWholeMilliseconds
    }
}
