package com.otus.mail.feature.inbox.domain

import com.otus.mail.core.preferences.StringMapStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * A message handed over for sending, kept on the device until the server has it. It goes once
 * [dueMillis] has passed, or at [sendAtMillis], a time the user chose, which the server holds it
 * for. [attempted] once a send started: the server may have taken it even if no answer came, so it
 * is looked for before it goes again. [problem] when it needs the user.
 */
@Serializable
data class OutboxEntry(
    val id: String,
    val draft: Draft,
    val answered: AnsweredMessage? = null,
    val dueMillis: Long,
    val sendAtMillis: Long? = null,
    val attempted: Boolean = false,
    val problem: OutboxProblem? = null,
) {
    /** The time the message was given, or null when it goes as soon as it is due. */
    val sendAt: Instant? get() = sendAtMillis?.let(Instant::fromEpochMilliseconds)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = ListSerializer(serializer())

        /** [entries] as one text value for an [OutboxStore]. */
        fun encode(entries: List<OutboxEntry>): String = json.encodeToString(serializer, entries)

        /** The entries [encode] wrote; none when [text] is missing or no longer reads. */
        fun decode(text: String?): List<OutboxEntry> =
            text?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
    }
}

/** Why a message in the outbox waits for the user instead of the network. */
enum class OutboxProblem {
    /** The server refused it, as for an unknown recipient. */
    Refused,

    /** A send may have gone, and the server could not say whether it did. */
    Unconfirmed,
}

/** Where the outbox keeps its messages, under [ENTRIES]. It must survive the process. */
interface OutboxStore : StringMapStore {
    companion object {
        const val ENTRIES = "entries"
    }
}

/** What the server says of a send whose answer was lost. */
enum class SendCheck {
    /** The server took it: it went, or it waits there for its time. */
    Sent,

    /** It never reached the server, or the server refused it, so it can go again. */
    NotSent,

    /** The server could not be asked; ask again later. */
    Unreachable,

    /** The server cannot look the message up, so only the user can tell. */
    Unconfirmable,
}

/** Looks on the server for a draft sent with its [Draft.messageId]. */
fun interface SendChecker {
    suspend fun check(draft: Draft): SendCheck
}
