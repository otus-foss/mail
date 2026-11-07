package com.otus.mail.feature.contacts.presentation

import com.otus.avatar.ContactAvatar
import com.otus.avatar.withAvatar
import com.otus.jscontact.Card
import com.otus.jscontact.CardKind
import com.otus.mail.feature.contacts.domain.BookKey
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactDraft
import com.otus.mail.feature.contacts.domain.ContactEdit
import com.otus.mail.feature.contacts.domain.DraftValue
import com.otus.mail.feature.contacts.domain.MergePlan
import com.otus.mail.feature.contacts.domain.photoKey

/**
 * Saves [draft] over [existing], or as a new card in [book]. Returns the card's key, which for a
 * new card is a device key until the server names it.
 */
internal fun ContactsState.save(
    draft: ContactDraft,
    existing: ContactCard?,
    book: BookKey,
): CardKey {
    val edit =
        if (existing == null) {
            ContactEdit.Create(newId(), book, draft.applyTo(Card(kind = CardKind.Individual, uid = newUid())))
        } else {
            val moved = book !in existing.books
            val after = draft.applyTo(existing.card)
            ContactEdit.Update(
                newId(),
                existing.key,
                existing.card,
                if (moved) after.copy(addressBookIds = mapOf(book.bookId to true)) else after,
            )
        }
    send(edit)
    return edit.key
}

/** Removes the cards of [keys]; with [waitsFor], only once the server accepts that change. */
internal fun ContactsState.delete(
    keys: Collection<CardKey>,
    waitsFor: String? = null,
) {
    keys.forEach { key -> send(ContactEdit.Delete(newId(), key, waitsFor)) }
    sync.unlink(keys)
}

/**
 * Saves [plan]'s merged card, then removes the others if the plan says so. The removals wait for
 * the merged card, so a refused merge leaves the cards as they were.
 */
internal fun ContactsState.merge(plan: MergePlan): CardKey {
    val merged = plan.merged()
    val target = plan.target
    val key =
        if (target == null) {
            val created = ContactEdit.Create(newId(), plan.book, merged.copy(id = null))
            send(created)
            created.key
        } else {
            send(ContactEdit.Update(newId(), target.key, target.card, merged))
            target.key
        }
    // The queue may fold the merge into a change of the target that was already waiting.
    delete(plan.removed.map { it.key }, sync.edits.lastOrNull { it.key == key }?.id)
    sync.dismiss(plan.cards.map { it.key }.toSet())
    return key
}

/** Saves [after] over [card], at once on screen and on the server once it is reachable. */
internal fun ContactsState.update(
    card: ContactCard,
    after: Card,
) = send(ContactEdit.Update(newId(), card.key, card.card, after))

/** Saves a refused change as a new card in [book] instead. */
internal fun ContactsState.saveElsewhere(
    key: CardKey,
    book: BookKey,
) {
    val card = card(key) ?: return
    sync.drop(key)
    send(ContactEdit.Create(newId(), book, card.card.copy(id = null, uid = newUid())))
}

/** Drops the refused change of [key], showing the card as the server has it. */
internal fun ContactsState.discard(key: CardKey) = sync.drop(key)

/** Gives [card] [avatar], a photo or a style, or takes its look away when null. */
internal fun ContactsState.setAvatar(
    card: ContactCard,
    avatar: ContactAvatar?,
) = update(card, card.card.withAvatar(avatar, card.card.photoKey))

/** Adds [address] to [card], keeping its other addresses and which one is preferred. */
internal fun ContactsState.addAddress(
    card: ContactCard,
    address: String,
) {
    val draft = ContactDraft.of(card)
    save(draft.copy(emails = draft.emails + DraftValue(value = address)), card, card.books.first())
}

/**
 * Removes the custom [label] from every card the user may change; those values keep no label.
 * Cards in read-only books keep it, so it may still be offered afterwards.
 */
internal fun ContactsState.deleteLabel(label: String) {
    withLabel(label)
        .filter { canWrite(it) }
        .forEach { card ->
            save(ContactDraft.of(card).withoutLabel(label), card, card.books.first())
        }
}
