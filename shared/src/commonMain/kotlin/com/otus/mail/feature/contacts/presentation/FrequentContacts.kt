package com.otus.mail.feature.contacts.presentation

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.jscontact.JsContactJson
import com.otus.mail.feature.contacts.domain.ContactStore
import com.otus.mail.feature.contacts.domain.FrequentScan
import com.otus.mail.feature.contacts.domain.MailTraceSource
import com.otus.mail.feature.contacts.domain.frequencyScores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
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
    private val store: ContactStore?,
    private val scope: CoroutineScope,
    private val now: () -> Instant = { Clock.System.now() },
) {
    private var scans by mutableStateOf(decode(store?.read()?.get(ContactStore.FREQUENT)))
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
                val due =
                    logins.filter { id ->
                        val scan = scans[id]
                        scan == null || now().toEpochMilliseconds() - scan.atMillis >= MAX_AGE
                    }
                val fresh =
                    due.mapNotNull { id ->
                        source.traces(id)?.let { traces ->
                            id to FrequentScan(now().toEpochMilliseconds(), frequencyScores(traces, now()))
                        }
                    }
                scans = (scans + fresh).filterKeys { it in logins }
                if (store != null) store.write(store.read() + (ContactStore.FREQUENT to encode(scans)))
            } finally {
                scanning = false
            }
        }
    }

    private companion object {
        val MAX_AGE = 1.days.inWholeMilliseconds
        val serializer = MapSerializer(String.serializer(), FrequentScan.serializer())

        fun encode(scans: Map<String, FrequentScan>): String = JsContactJson.encodeToString(serializer, scans)

        fun decode(saved: String?): Map<String, FrequentScan> =
            saved?.let { runCatching { JsContactJson.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
    }
}
