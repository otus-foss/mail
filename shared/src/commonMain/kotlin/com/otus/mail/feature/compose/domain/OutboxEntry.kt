package com.otus.mail.feature.compose.domain

import com.otus.mail.core.messages.domain.AnsweredMessage
import com.otus.mail.core.messages.domain.Draft
import com.otus.mail.core.messages.domain.answered
import com.otus.storage.StringMapStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * A message handed over for sending, kept on the device until the server has it. It goes once
 * [dueMillis] has passed, or at [sendAtMillis], a time the user chose, which the server holds it
 * for. [attempted] once a send started: the server may have taken it even if no answer came, so it
 * is looked for before it goes again. [storedCopy] once the server stored the copy that try
 * submits, so the lookup goes by its id. [problem] when it needs the user. [withdrawn] after Undo
 * of a message the server may hold: it never goes, and stays only until a hold found there is
 * canceled.
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
    val withdrawn: Boolean = false,
    val storedCopy: StoredCopy? = null,
) {
    /** The time the message was given, or null when it goes as soon as it is due. */
    val sendAt: Instant? get() = sendAtMillis?.let(Instant::fromEpochMilliseconds)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = ListSerializer(serializer())

        /** [entries] as one text value for an [OutboxStore]. */
        fun encode(entries: List<OutboxEntry>): String = json.encodeToString(serializer, entries)

        /** The entries [encode] wrote; none when [text] is missing or no longer reads. */
        fun decode(text: String?): List<OutboxEntry> = text?.let(::decodeOrNull).orEmpty()

        /** The entries [encode] wrote in [text]; null when it no longer reads. */
        fun decodeOrNull(text: String): List<OutboxEntry>? =
            runCatching { json.decodeFromString(serializer, text) }.getOrNull()
    }
}

/** The Email [emailId] a send stored on the server at [atMillis], before it submitted that copy. */
@Serializable
data class StoredCopy(
    val emailId: String,
    val atMillis: Long,
)

/** Why a message in the outbox waits for the user instead of the network. */
enum class OutboxProblem {
    /** The server refused it, as for an unknown recipient. */
    Refused,

    /** A send may have gone, and the server could not say whether it did. */
    Unconfirmed,
}

/**
 * Where the outbox keeps its messages, under [ENTRIES]. It must survive the process. Messages that
 * no longer read, as after a change of their format, move to [UNREADABLE], or the next free key of
 * [unreadableKeys] when an earlier set is there, rather than being lost.
 */
interface OutboxStore : StringMapStore {
    companion object {
        const val ENTRIES = "entries"
        const val UNREADABLE = "unreadable"

        /** [UNREADABLE], then "unreadable-1", "unreadable-2", and so on. */
        fun unreadableKeys(): Sequence<String> =
            generateSequence(0) { it + 1 }.map { if (it == 0) UNREADABLE else "$UNREADABLE-$it" }
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

/**
 * Looks on the server for a draft sent with its [Draft.messageId]. A copy that went is tidied up
 * as after a send: the saved draft goes and the message it [answered] gets its mark. With
 * [cancelHeld], as after Undo, a copy the server still holds is canceled and removed, and counts
 * as never sent.
 */
fun interface SendChecker {
    suspend fun check(
        draft: Draft,
        answered: AnsweredMessage?,
        cancelHeld: Boolean,
    ): SendCheck

    /** [check], by the id of the [storedCopy] the earlier try submitted when the server named it. */
    suspend fun check(
        draft: Draft,
        answered: AnsweredMessage?,
        cancelHeld: Boolean,
        storedCopy: String?,
    ): SendCheck = check(draft, answered, cancelHeld)
}
