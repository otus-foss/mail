package com.otusfoss.mail.feature.contacts.presentation

import com.otusfoss.mail.feature.contacts.domain.CardKey

/** Shows [keys] as one person without changing the cards. */
internal fun ContactsState.showAsOne(keys: Set<CardKey>) = sync.link(keys)

/** Lists [keys] as separate people again, undoing [showAsOne]. */
internal fun ContactsState.showSeparately(keys: Collection<CardKey>) = sync.unlink(keys)

/** Stops suggesting that [keys] are the same person. */
internal fun ContactsState.notDuplicates(keys: Set<CardKey>) = sync.dismiss(keys)
