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
            is ContactEdit.Create -> {
                shown + ContactCard(edit.key, edit.card, listOf(edit.book), sync)
            }

            is ContactEdit.Update -> {
                shown.map {
                    if (it.key ==
                        edit.key
                    ) {
                        it.copy(card = edit.after, books = it.booksAfter(edit.after), sync = sync)
                    } else {
                        it
                    }
                }
            }

            is ContactEdit.Delete -> {
                if (sync is CardSync.Refused) {
                    shown.map { if (it.key == edit.key) it.copy(sync = sync) else it }
                } else {
                    shown.filterNot { it.key == edit.key }
                }
            }
        }
    }

/**
 * [edits] with [edit] added, folded into a waiting change of the same card so the server sees one
 * change per card: an update of a card not sent yet changes its creation, a second update keeps
 * the first one's starting point, and deleting a card not sent yet drops it entirely.
 */
fun enqueue(
    edits: List<ContactEdit>,
    edit: ContactEdit,
): List<ContactEdit> {
    val earlier = edits.lastOrNull { it.key == edit.key }
    return when {
        earlier is ContactEdit.Create && edit is ContactEdit.Update -> {
            edits.map { if (it == earlier) earlier.copy(card = edit.after) else it }
        }

        earlier is ContactEdit.Create && edit is ContactEdit.Delete -> {
            edits.filterNot { it.key == edit.key }
        }

        earlier is ContactEdit.Update && edit is ContactEdit.Update -> {
            edits.map { if (it == earlier) earlier.copy(after = edit.after) else it }
        }

        earlier is ContactEdit.Update && edit is ContactEdit.Delete -> {
            edits.filterNot { it == earlier } + edit
        }

        else -> {
            edits + edit
        }
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
        is ContactEdit.Create -> {
            cards + ContactCard(requireNotNull(created), edit.card.copy(id = created.cardId), listOf(edit.book))
        }

        is ContactEdit.Update -> {
            cards.map { if (it.key == edit.key) it.copy(card = edit.after, books = it.booksAfter(edit.after)) else it }
        }

        is ContactEdit.Delete -> {
            cards.filterNot { it.key == edit.key }
        }
    }

/** The books [after] puts this card in, in the same account; its own books when [after] does not say. */
private fun ContactCard.booksAfter(after: Card): List<BookKey> =
    after.addressBookIds
        ?.filterValues { it }
        ?.keys
        ?.map { BookKey(key.loginId, key.accountId, it) }
        ?.takeIf { it.isNotEmpty() }
        ?: books
