package com.otusfoss.mail.feature.compose.domain

import com.otusfoss.mail.core.messages.domain.AnsweredMessage
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.SentMessage
import com.otusfoss.mail.core.messages.domain.answered
import com.otusfoss.mail.core.storage.StoreField
import com.otusfoss.mail.core.storage.StoreSpec
import com.otusfoss.mail.core.storage.TextCodec
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * A message handed over for sending, kept on the device until its send is final on the server. It
 * goes once [dueMillis] has passed, or at [sendAtMillis], a time the user chose, which the server
 * holds it for. [attempted] once a send started: the server may have taken it even if no answer
 * came, so it is looked for before it goes again. [storedCopy] once the server stored the copy that
 * try submits, so the lookup goes by its id. [problem] when it needs the user.
 *
 * [held] once the server holds it for Undo, which ends at [dueMillis]: the message stays until
 * that hold is over, so Undo and sending it early work from here, also after a restart. What the
 * user asks of a waiting message is kept before the server hears of it. [withdrawn] after Undo: it
 * never goes, and stays only until a hold the server has, or may have, is canceled. [sendNow]
 * after the user asked for a held message to go at once, until the server sent it.
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
    val held: SentMessage? = null,
    val sendNow: Boolean = false,
) {
    /** The time the message was given, or null when it goes as soon as it is due. */
    val sendAt: Instant? get() = sendAtMillis?.let(Instant::fromEpochMilliseconds)

    /** When it is due, which for a [held] message is the end of its Undo window. */
    val due: Instant get() = Instant.fromEpochMilliseconds(dueMillis)

    /** Whether the server holds it and the user asked nothing more of it, so it goes by itself. */
    val goesByItself: Boolean get() = held != null && !withdrawn && !sendNow

    /** Whether it waits for the server to answer, so a background run should try again. */
    val awaitsServer: Boolean get() = problem == null && attempted && !goesByItself
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
object OutboxStore : StoreSpec<String, String>("outbox", TextCodec.Text, TextCodec.Text, commit = true) {
    val ENTRIES = StoreField("entries", TextCodec.json(ListSerializer(OutboxEntry.serializer()), lenientJson))

    const val UNREADABLE = "unreadable"

    /** [UNREADABLE], then "unreadable-1", "unreadable-2", and so on. */
    fun unreadableKeys(): Sequence<String> =
        generateSequence(0) { it + 1 }.map { if (it == 0) UNREADABLE else "$UNREADABLE-$it" }
}

private val lenientJson = Json { ignoreUnknownKeys = true }

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
