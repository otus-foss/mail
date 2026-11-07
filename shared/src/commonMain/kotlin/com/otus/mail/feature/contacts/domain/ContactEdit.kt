package com.otus.mail.feature.contacts.domain

import com.otus.jscontact.Card
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

    /**
     * A new [card] in [book]. [sent] is the card as a try sent it, kept until the server answers,
     * since a lost answer may hide that the server stored it.
     */
    @Serializable
    @SerialName("create")
    data class Create(
        override val id: String,
        val book: BookKey,
        val card: Card,
        val sent: Card? = null,
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

        private const val LOCAL_PREFIX = "local:"
    }
}

/**
 * Whether this is a delete waiting for a change the server refused. It waits for the user to save
 * or drop that change, not for a connection, so its card still shows.
 */
fun ContactEdit.isHeld(refused: Map<String, ContactWriteFailure>): Boolean =
    this is ContactEdit.Delete && waitsFor?.let { it in refused } == true
