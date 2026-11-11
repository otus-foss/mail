package com.otusfoss.mail.feature.compose.domain

import com.otusfoss.mail.core.storage.DeviceStore
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * The outbox's messages in [store], read and written on [io]; without a store they last as long as
 * the process. It also knows which messages the user was offered in this process.
 */
internal class OutboxEntries(
    private val store: DeviceStore<String, String>?,
    private val io: CoroutineContext,
) {
    private var memory = emptyList<OutboxEntry>()

    // Messages already offered to the user, so a recreated screen does not offer them twice.
    private var offered = emptySet<String>()

    suspend fun all(): List<OutboxEntry> {
        val saved = store ?: return memory
        return withContext(io) {
            val values = saved.read()
            val text = values[OutboxStore.ENTRIES.key]
            val entries = OutboxStore.ENTRIES.of(values)
            // Saving over messages that no longer read would lose them, so they are set aside first,
            // under a key of their own, beside any set aside before.
            if (text != null && entries == null) {
                val aside = OutboxStore.unreadableKeys().first { it !in values }
                saved.write(values - OutboxStore.ENTRIES.key + (aside to text))
            }
            entries.orEmpty()
        }
    }

    suspend fun save(entries: List<OutboxEntry>) {
        memory = entries
        val saved = store ?: return
        withContext(io) { saved.update { it + OutboxStore.ENTRIES.entry(entries) } }
    }

    suspend fun replace(entry: OutboxEntry) = save(all().map { if (it.id == entry.id) entry else it })

    suspend fun removeIf(predicate: (OutboxEntry) -> Boolean) = save(all().filterNot(predicate))

    /** Keeps [entry] with [problem]. The failed send's caller tells the user, so it is not offered again. */
    suspend fun fail(
        entry: OutboxEntry,
        problem: OutboxProblem,
    ) {
        replace(entry.copy(problem = problem))
        offered = offered + entry.id
    }

    /** The messages that need the user and were not offered before in this process, now offered. */
    suspend fun unoffered(): List<OutboxEntry> {
        val found = all().filter { it.problem != null && it.id !in offered }
        offered = offered + found.map { it.id }
        return found
    }
}
