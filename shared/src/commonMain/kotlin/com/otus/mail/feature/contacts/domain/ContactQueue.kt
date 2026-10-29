package com.otus.mail.feature.contacts.domain

import com.otus.jscontact.Card

/**
 * [cards] as the user sees them: the server's cards with each waiting change in [edits] applied
 * in order. A change in [refused] still shows, marked with why the server refused it, so the user
 * can save it elsewhere or drop it.
 */
fun applyEdits(
    cards: List<ContactCard>,
    edits: List<ContactEdit>,
    refused: Map<String, ContactWriteFailure> = emptyMap(),
): List<ContactCard> =
    edits.fold(cards) { shown, edit ->
        val sync = refused[edit.id]?.let(CardSync::Refused) ?: CardSync.Waiting
        when (edit) {
            is ContactEdit.Create -> shown + ContactCard(edit.key, edit.card, listOf(edit.book), sync)
            is ContactEdit.Update -> shown.updated(edit, sync)
            is ContactEdit.Delete -> shown.deleted(edit, sync, refused)
        }
    }

private fun List<ContactCard>.updated(
    edit: ContactEdit.Update,
    sync: CardSync,
): List<ContactCard> =
    map { card ->
        if (card.key != edit.key) return@map card
        card.copy(card = edit.after, books = card.booksAfter(edit.after), sync = sync)
    }

private fun List<ContactCard>.deleted(
    edit: ContactEdit.Delete,
    sync: CardSync,
    refused: Map<String, ContactWriteFailure>,
): List<ContactCard> =
    when {
        edit.isHeld(refused) -> this
        sync is CardSync.Refused -> map { if (it.key == edit.key) it.copy(sync = sync) else it }
        else -> filterNot { it.key == edit.key }
    }

/**
 * [edits] with [edit] added, folded into a waiting change of the same card so the server sees one
 * change per card: an update of a card not sent yet changes its creation, a second update keeps
 * the first one's starting point, and deleting a card not sent yet drops it entirely. The change
 * [sending] names is on its way to the server, so [edit] waits after it instead of joining it.
 */
fun enqueue(
    edits: List<ContactEdit>,
    edit: ContactEdit,
    sending: String? = null,
): List<ContactEdit> {
    val earlier = edits.lastOrNull { it.key == edit.key }?.takeIf { it.id != sending }
    val created = earlier as? ContactEdit.Create
    val revised = earlier as? ContactEdit.Update
    val update = edit as? ContactEdit.Update
    return when {
        created != null && update != null -> edits.map { if (it == created) created.copy(card = update.after) else it }
        earlier is ContactEdit.Create && edit is ContactEdit.Delete -> edits.filterNot { it.key == edit.key }
        revised != null && update != null -> edits.map { if (it == revised) revised.copy(after = update.after) else it }
        earlier is ContactEdit.Update && edit is ContactEdit.Delete -> edits.filterNot { it == earlier } + edit
        else -> edits + edit
    }
}

/** [edits] pointed at [created] where they named the card [local] before the server named it. */
fun rekey(
    edits: List<ContactEdit>,
    local: CardKey,
    created: CardKey,
): List<ContactEdit> =
    edits.map { edit ->
        when {
            edit.key != local -> edit
            edit is ContactEdit.Update -> edit.copy(key = created)
            edit is ContactEdit.Delete -> edit.copy(key = created)
            else -> edit
        }
    }

/** [cards] as the server holds them once it accepted [edit]; [created] is a new card's key. */
fun afterSent(
    cards: List<ContactCard>,
    edit: ContactEdit,
    created: CardKey?,
): List<ContactCard> =
    when (edit) {
        is ContactEdit.Create -> cards + edit.sentCard(created)
        is ContactEdit.Update -> cards.afterUpdate(edit)
        is ContactEdit.Delete -> cards.filterNot { it.key == edit.key }
    }

private fun ContactEdit.Create.sentCard(created: CardKey?): ContactCard {
    val key = requireNotNull(created)
    return ContactCard(key, card.copy(id = key.cardId), listOf(book))
}

private fun List<ContactCard>.afterUpdate(edit: ContactEdit.Update): List<ContactCard> =
    map { if (it.key == edit.key) it.copy(card = edit.after, books = it.booksAfter(edit.after)) else it }

/** The books [after] puts this card in, in the same account; its own books when [after] does not say. */
private fun ContactCard.booksAfter(after: Card): List<BookKey> =
    after.addressBookIds
        ?.filterValues { it }
        ?.keys
        ?.map { BookKey(key.loginId, key.accountId, it) }
        ?.takeIf { it.isNotEmpty() }
        ?: books
