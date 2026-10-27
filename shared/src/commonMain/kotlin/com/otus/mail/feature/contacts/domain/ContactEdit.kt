package com.otus.mail.feature.contacts.domain

import com.otus.jscontact.Card
import com.otus.jscontact.JsContactJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * A change made on the device that the server has not confirmed yet. Changes wait in order and
 * survive a restart, so an edit made offline is sent once there is a connection. [id] tells the
 * changes apart; a created card is known by `local:<id>` until the server names it.
 */
@Serializable
sealed interface ContactEdit {
    val id: String

    /** The card the change is about. */
    val key: CardKey

    @Serializable
    @SerialName("create")
    data class Create(
        override val id: String,
        val book: BookKey,
        val card: Card,
    ) : ContactEdit {
        override val key: CardKey get() = CardKey(book.loginId, book.accountId, localId(id))
    }

    @Serializable
    @SerialName("update")
    data class Update(
        override val id: String,
        override val key: CardKey,
        val before: Card,
        val after: Card,
    ) : ContactEdit

    /**
     * Removes the card. [waitsFor] names a change this one depends on, such as the merged card a
     * merge saves: while the server refuses that change, the card is not removed.
     */
    @Serializable
    @SerialName("delete")
    data class Delete(
        override val id: String,
        override val key: CardKey,
        val waitsFor: String? = null,
    ) : ContactEdit

    companion object {
        fun localId(id: String): String = "$LOCAL_PREFIX$id"

        fun isLocal(cardId: String): Boolean = cardId.startsWith(LOCAL_PREFIX)

        /** [edits] as one text value for a [ContactStore]. */
        fun encode(edits: List<ContactEdit>): String = JsContactJson.encodeToString(ListSerializer(serializer()), edits)

        /** Changes saved by [encode]; an unreadable value counts as none rather than stopping the app. */
        fun decode(saved: String?): List<ContactEdit> =
            saved
                ?.let { runCatching { JsContactJson.decodeFromString(ListSerializer(serializer()), it) }.getOrNull() }
                .orEmpty()

        private const val LOCAL_PREFIX = "local:"
    }
}

/**
 * Whether this is a delete waiting for a change the server refused. It waits for the user to save
 * or drop that change, not for a connection, so its card still shows.
 */
fun ContactEdit.isHeld(refused: Map<String, ContactWriteFailure>): Boolean =
    this is ContactEdit.Delete && waitsFor?.let { it in refused } == true

/**
 * The device's own contact data: changes waiting for the server, cards shown as one person, and
 * duplicate pairs the user said are different people. Values are text under fixed keys.
 */
interface ContactStore {
    fun read(): Map<String, String>

    fun write(values: Map<String, String>)

    companion object {
        const val EDITS = "edits"
        const val LINKS = "links"
        const val DISMISSED = "dismissed"
        const val FREQUENT = "frequent"
    }
}

/**
 * Drops [loginId]'s waiting changes, and its cards from those shown as one and from dismissed
 * pairs, so nothing of a removed account stays on the device.
 */
fun ContactStore.forgetAccount(loginId: String) {
    val saved = read()

    fun others(name: String) =
        KeySets.encode(
            KeySets.decode(saved[name]).map { set -> set.filterNot { it.loginId == loginId }.toSet() }.filter {
                it.size >
                    1
            },
        )
    val edits = ContactEdit.decode(saved[ContactStore.EDITS]).filterNot { it.key.loginId == loginId }
    write(
        saved +
            mapOf(
                ContactStore.EDITS to ContactEdit.encode(edits),
                ContactStore.LINKS to others(ContactStore.LINKS),
                ContactStore.DISMISSED to others(ContactStore.DISMISSED),
            ),
    )
}

/** Sets of card keys as JSON, for [ContactLinks] and dismissed duplicates. */
internal object KeySets {
    private val serializer = ListSerializer(ListSerializer(CardKey.serializer()))

    fun encode(sets: List<Set<CardKey>>): String = JsContactJson.encodeToString(serializer, sets.map { it.toList() })

    fun decode(saved: String?): List<Set<CardKey>> =
        saved
            ?.let { runCatching { JsContactJson.decodeFromString(serializer, it) }.getOrNull() }
            .orEmpty()
            .map { it.toSet() }
}
