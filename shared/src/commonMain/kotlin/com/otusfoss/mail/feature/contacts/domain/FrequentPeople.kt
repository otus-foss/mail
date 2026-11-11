package com.otusfoss.mail.feature.contacts.domain

import com.otusfoss.mail.core.mail.EmailAddressKey
import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** One email as the frequency scan reads it: when it arrived and the addresses on it. */
data class MailTrace(
    val at: Instant,
    val from: List<String> = emptyList(),
    val to: List<String> = emptyList(),
    val copied: List<String> = emptyList(),
)

/** A login's newest [sent] and [received] mail, the user's own mail apart from the rest. */
class MailTraces(
    val sent: List<MailTrace>,
    val received: List<MailTrace>,
)

/** Reads the mail the frequency scan needs. */
interface MailTraceSource {
    /** The logins to scan. */
    suspend fun logins(): List<String>

    /** [loginId]'s newest mail, or null when it has no mail or cannot be reached. */
    suspend fun traces(loginId: String): MailTraces?
}

/** A login's scores and when they were worked out, as the device keeps them. */
@Serializable
data class FrequentScan(
    val atMillis: Long,
    val scores: Map<String, Double>,
)

/**
 * How much the user deals with each address, by [EmailAddressKey] value. Writing counts most: each sent
 * email gives its To recipients [SENT_TO] and its Cc and Bcc recipients [SENT_COPY], shared out by
 * the square root of how many it went to, so a long list counts for little. Received mail counts
 * [RECEIVED] per email, and only from people the user wrote to, so notifications and no-reply
 * senders score nothing. Every email counts less the older it is, by half each [HALF_LIFE]. The
 * user's own addresses, everyone a sent email came from, never score.
 */
fun frequencyScores(
    traces: MailTraces,
    now: Instant,
): Map<String, Double> {
    val own =
        traces.sent
            .flatMap { it.from }
            .map(::key)
            .toSet()
    val weight = { trace: MailTrace -> 0.5.pow((now - trace.at).coerceAtLeast(0.days) / HALF_LIFE) }
    val sent =
        traces.sent.flatMap { trace ->
            val share = weight(trace) / sqrt((trace.to.size + trace.copied.size).coerceAtLeast(1).toDouble())
            trace.to.map { it to SENT_TO * share } + trace.copied.map { it to SENT_COPY * share }
        }
    val written = sent.map { key(it.first) }.toSet()
    val received =
        traces.received.flatMap { trace ->
            trace.from.filter { key(it) in written }.map { it to RECEIVED * weight(trace) }
        }
    return (sent + received)
        .map { (address, score) -> key(address) to score }
        .filter { (address, _) -> address.isNotEmpty() && address !in own }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, scores) -> scores.sum() }
}

private fun key(address: String): String = EmailAddressKey(address).value

private const val SENT_TO = 10.0
private const val SENT_COPY = 5.0
private const val RECEIVED = 1.0
private val HALF_LIFE = 90.days
