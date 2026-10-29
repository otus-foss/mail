package com.otus.mail.feature.contacts.presentation.components

import com.otus.mail.feature.contacts.domain.ContactBook
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactPerson

/** What one person's page shows besides the header: their books, groups, and a refused change. */
internal class PersonFacts(
    val person: ContactPerson,
    val booksOf: (ContactCard) -> List<ContactBook>,
    val groups: List<ContactCard>,
    val saveElsewhere: ContactBook?,
    /** Whether the person's card holds the avatar of one of the user's own addresses. */
    val ownAvatar: Boolean = false,
)
