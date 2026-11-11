package com.otusfoss.mail.feature.contacts.presentation.components

import com.otusfoss.mail.feature.contacts.domain.BookKey
import com.otusfoss.mail.feature.contacts.domain.ContactBook

/**
 * The address books the Contacts header can pick from, the [chosen] ones (none means every book),
 * where a new pick goes, and how to open the full filter list.
 */
internal class BookFilter(
    val books: List<ContactBook>,
    val chosen: Set<BookKey>,
    val onChange: (Set<BookKey>) -> Unit,
    val onOpenFilters: () -> Unit,
)
